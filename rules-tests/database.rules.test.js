import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';

// Player IDs are the anonymous-auth uids themselves.
const ALICE    = 'aliceUid0000000000000000001';
const BOB      = 'bobUid000000000000000000002';
const STRANGER = 'strangerUid0000000000000003';
const SID   = 'session-1';
const DAY   = 24 * 60 * 60 * 1000;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-mtglifetracker',
    database: { rules: readFileSync(new URL('../database.rules.json', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

beforeEach(async () => {
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(ctx => ctx.database().ref().set({
    sessionCodes: { ABCDEFGH: SID },
    sessions: {
      [SID]: {
        code: 'ABCDEFGH',
        createdAt: Date.now(),
        hostUserId: ALICE,
        game: 0,
        settings: { startLife: 20, commanderDeathThreshold: 21, infectDeathThreshold: 10, maxPlayers: 0 },
        users: {
          [ALICE]: { displayName: 'Alice', life: 20, online: true },
          [BOB]:   { displayName: 'Bob',   life: 20, online: true },
        },
      },
    },
  }));
});

const alice    = () => env.authenticatedContext(ALICE).database();
const bob      = () => env.authenticatedContext(BOB).database();
const stranger = () => env.authenticatedContext(STRANGER).database();
const anon     = () => env.unauthenticatedContext().database();
const seed     = data => env.withSecurityRulesDisabled(ctx => ctx.database().ref().update(data));

describe('reading (#61)', () => {
  test('signed-out clients cannot read anything', async () => {
    await assertFails(anon().ref(`sessions/${SID}`).get());
    await assertFails(anon().ref('sessionCodes/ABCDEFGH').get());
    await assertFails(anon().ref(`presence/${ALICE}`).get());
    await assertFails(anon().ref('sessions').get());
  });

  test('signed-in clients can read a session, a code and presence', async () => {
    await assertSucceeds(stranger().ref(`sessions/${SID}`).get());
    await assertSucceeds(stranger().ref('sessionCodes/ABCDEFGH').get());
    await assertSucceeds(stranger().ref(`presence/${ALICE}`).get());
  });

  test('nobody can list every session', async () => {
    await assertFails(alice().ref('sessions').get());
  });
});

describe('player identity (#55, #60)', () => {
  test('nobody can create a seat under someone else\'s ID', async () => {
    await assertFails(stranger().ref(`sessions/${SID}/users/someoneElse`).set({ displayName: 'X', life: 20 }));
  });

  test('the old userAuth mapping is gone', async () => {
    await assertFails(stranger().ref(`userAuth/${STRANGER}`).set(STRANGER));
  });
});

describe('player seats (#55)', () => {
  const seat = id => `sessions/${SID}/users/${id}`;

  test('a player can update their own seat', async () => {
    await assertSucceeds(alice().ref(`${seat(ALICE)}/life`).set(17));
    await assertSucceeds(alice().ref(`${seat(ALICE)}/customStats/poison`).set(3));
    await assertSucceeds(alice().ref(`${seat(ALICE)}/conceded`).set(true));
  });

  test("a player cannot change someone else's seat", async () => {
    await assertFails(bob().ref(`${seat(ALICE)}/life`).set(0));
    await assertFails(bob().ref(`${seat(ALICE)}/conceded`).set(true));
    await assertFails(bob().ref(`${seat(ALICE)}/displayName`).set('Loser'));
    await assertFails(stranger().ref(`${seat(ALICE)}/life`).set(0));
  });

  test('a player cannot remove someone who is online', async () => {
    await assertFails(bob().ref(seat(ALICE)).remove());
  });

  test('anyone in the game can clear an offline ghost (#53)', async () => {
    await seed({ [`${seat(ALICE)}/online`]: false });
    await assertSucceeds(bob().ref(seat(ALICE)).remove());
  });

  test('a player can leave', async () => {
    await assertSucceeds(alice().ref(seat(ALICE)).remove());
  });

  test('a fresh seat needs a name and a life total', async () => {
    await assertFails(stranger().ref(`${seat(STRANGER)}/online`).set(false));
    await assertSucceeds(stranger().ref(seat(STRANGER)).set({ displayName: 'Carol', life: 40, online: true }));
  });

  test('unknown fields and oversized values are rejected', async () => {
    await assertFails(alice().ref(`${seat(ALICE)}/isAdmin`).set(true));
    await assertFails(alice().ref(`${seat(ALICE)}/displayName`).set('x'.repeat(65)));
    await assertFails(alice().ref(`${seat(ALICE)}/displayName`).set(''));
    await assertFails(alice().ref(`${seat(ALICE)}/life`).set(-1));
    await assertFails(alice().ref(`${seat(ALICE)}/life`).set('20'));
  });
});

describe('stat names (#57) and per-player counters (#29)', () => {
  const def = name => alice().ref(`sessions/${SID}/users/${ALICE}/stats/${name}`);

  test('presets and ordinary custom names are accepted', async () => {
    await assertSucceeds(def('commander').set('NUMERIC'));
    await assertSucceeds(def('poison').set('NUMERIC'));
    await assertSucceeds(def('ring').set('RING_STAGE'));
    await assertSucceeds(def("Lore Counters").set('NUMERIC'));
  });

  test('names that shadow player fields are rejected in any case', async () => {
    for (const name of ['life', 'Life', 'LIFE', 'displayName', 'conceded', 'customStats']) {
      await assertFails(def(name).set('NUMERIC'));
    }
  });

  test('odd characters, long names and unknown types are rejected', async () => {
    await assertFails(def('gold!').set('NUMERIC'));
    await assertFails(def('x'.repeat(33)).set('NUMERIC'));
    await assertFails(def('gold').set('COUNTER'));
  });

  test('stat values on a seat follow the same naming rule', async () => {
    await assertFails(alice().ref(`sessions/${SID}/users/${ALICE}/customStats/gold!`).set(1));
  });

  test('a player turns counters on and off on their own seat only', async () => {
    await assertSucceeds(def('gold').set('NUMERIC'));
    await assertSucceeds(def('gold').remove());
    await assertFails(alice().ref(`sessions/${SID}/users/${BOB}/stats/gold`).set('NUMERIC'));
  });

  test('the shared stat list is gone', async () => {
    await assertFails(alice().ref(`sessions/${SID}/customStatNames/gold`).set('NUMERIC'));
  });

  test('any player can turn Day/Night off again (undo)', async () => {
    const dayNight = alice().ref(`sessions/${SID}/globalStats/daynight`);
    await assertSucceeds(dayNight.set(0));
    await assertSucceeds(dayNight.remove());
  });
});

describe('observers (#6)', () => {
  const watcher = (db, uid) => db.ref(`sessions/${SID}/observers/${uid}`);

  test('anyone signed in can start and stop watching under their own uid', async () => {
    await assertSucceeds(watcher(stranger(), STRANGER).set('Sam'));
    await assertSucceeds(watcher(stranger(), STRANGER).remove());
  });

  test('nobody can add or remove someone else as an observer', async () => {
    await seed({ [`sessions/${SID}/observers/${STRANGER}`]: 'Sam' });
    await assertFails(watcher(alice(), STRANGER).remove());
    await assertFails(watcher(alice(), BOB).set('Bob'));
  });

  test('observer names follow the display name limits', async () => {
    await assertFails(watcher(stranger(), STRANGER).set(''));
    await assertFails(watcher(stranger(), STRANGER).set('x'.repeat(65)));
    await assertFails(watcher(stranger(), STRANGER).set(3));
  });

  test('an observer can never be made the monarch', async () => {
    await seed({ [`sessions/${SID}/observers/${STRANGER}`]: 'Sam' });
    await assertFails(alice().ref(`sessions/${SID}/monarch`).set(STRANGER));
  });

  test('a session with only observers left can be deleted', async () => {
    await seed({ [`sessions/${SID}/users`]: null, [`sessions/${SID}/observers/${STRANGER}`]: 'Sam' });
    await assertSucceeds(stranger().ref(`sessions/${SID}`).remove());
  });
});

describe('monarch (#29)', () => {
  const monarch = db => db.ref(`sessions/${SID}/monarch`);

  test('any player can pass the monarch to someone seated, or end it', async () => {
    await assertSucceeds(monarch(alice()).set(ALICE));
    await assertSucceeds(monarch(bob()).set(BOB));
    await assertSucceeds(monarch(alice()).set(BOB));
    await assertSucceeds(monarch(bob()).remove());
  });

  test('the monarch must be a seated player', async () => {
    await assertFails(monarch(alice()).set(STRANGER));
    await assertFails(monarch(alice()).set(42));
  });
});

describe('session codes (#58, #59)', () => {
  test('a free code can be claimed with 8 characters', async () => {
    await assertSucceeds(alice().ref('sessionCodes/ZZZZ2345').set('session-2'));
  });

  test('legacy 6-character codes stay valid', async () => {
    await assertSucceeds(alice().ref('sessionCodes/ZZZ234').set('session-2'));
  });

  test('a code in use cannot be overwritten or released', async () => {
    await assertFails(bob().ref('sessionCodes/ABCDEFGH').set('session-2'));
    await assertFails(bob().ref('sessionCodes/ABCDEFGH').remove());
  });

  test("a code can be released once its session is gone", async () => {
    await seed({ [`sessions/${SID}`]: null });
    await assertSucceeds(bob().ref('sessionCodes/ABCDEFGH').remove());
  });

  test('malformed codes are rejected', async () => {
    await assertFails(alice().ref('sessionCodes/abc123').set('session-2'));
    await assertFails(alice().ref('sessionCodes/ABC').set('session-2'));
  });
});

describe('session lifecycle (#59)', () => {
  test('a signed-in client can create a session', async () => {
    await assertSucceeds(alice().ref('sessions/session-2').set({
      code: 'QWERTY23', createdAt: { '.sv': 'timestamp' },
    }));
  });

  test('a session needs a valid code and creation time', async () => {
    await assertFails(alice().ref('sessions/session-2').set({ code: 'QWERTY23' }));
    await assertFails(alice().ref('sessions/session-2').set({ code: 'bad', createdAt: { '.sv': 'timestamp' } }));
    await assertFails(anon().ref('sessions/session-2').set({ code: 'QWERTY23', createdAt: { '.sv': 'timestamp' } }));
  });

  test('a session with players in it cannot be deleted or replaced by a player', async () => {
    await assertFails(bob().ref(`sessions/${SID}`).remove());
    await assertFails(stranger().ref(`sessions/${SID}`).remove());
    await assertFails(alice().ref(`sessions/${SID}`).set({ code: 'NEWCODE2', createdAt: { '.sv': 'timestamp' } }));
    await assertFails(alice().ref(`sessions/${SID}/code`).set('NEWCODE2'));
  });

  test('the host can end a session with players in it', async () => {
    await assertSucceeds(alice().ref(`sessions/${SID}`).remove());
  });

  test('an empty session can be deleted', async () => {
    await seed({ [`sessions/${SID}/users`]: null });
    await assertSucceeds(alice().ref(`sessions/${SID}`).remove());
  });

  test('an abandoned session older than a day can be deleted', async () => {
    await seed({ [`sessions/${SID}/createdAt`]: Date.now() - 2 * DAY });
    await assertSucceeds(stranger().ref(`sessions/${SID}`).remove());
  });

  test('unknown session-level data is rejected', async () => {
    await assertFails(alice().ref(`sessions/${SID}/junk`).set('x'.repeat(1000)));
  });
});

describe('host controls and shared rules (#44, #46)', () => {
  const settings = { startLife: 40, commanderDeathThreshold: 21, infectDeathThreshold: 10, maxPlayers: 4 };

  test('the creator can make themselves host with shared rules', async () => {
    await assertSucceeds(alice().ref('sessions/session-2').set({
      code: 'QWERTY23', createdAt: { '.sv': 'timestamp' }, hostUserId: ALICE, game: 0, settings,
    }));
  });

  test('nobody can create a session with someone else as host', async () => {
    await assertFails(bob().ref('sessions/session-2').set({
      code: 'QWERTY23', createdAt: { '.sv': 'timestamp' }, hostUserId: ALICE, game: 0, settings,
    }));
  });

  test('the host cannot be changed', async () => {
    await assertFails(bob().ref(`sessions/${SID}/hostUserId`).set(BOB));
    await assertFails(alice().ref(`sessions/${SID}/hostUserId`).set(BOB));
  });

  test('only the host changes the rules', async () => {
    await assertSucceeds(alice().ref(`sessions/${SID}/settings`).set(settings));
    await assertFails(bob().ref(`sessions/${SID}/settings`).set(settings));
    await assertFails(bob().ref(`sessions/${SID}/settings/startLife`).set(1));
  });

  test('rules values are bounded', async () => {
    await assertFails(alice().ref(`sessions/${SID}/settings/startLife`).set(0));
    await assertFails(alice().ref(`sessions/${SID}/settings/maxPlayers`).set(100));
    await assertFails(alice().ref(`sessions/${SID}/settings/cheats`).set(true));
  });

  test('only the host starts a new game, and the counter only goes up', async () => {
    await assertSucceeds(alice().ref(`sessions/${SID}/game`).set(1));
    await assertFails(bob().ref(`sessions/${SID}/game`).set(2));
    await assertFails(alice().ref(`sessions/${SID}/game`).set(0));
  });

  test('the host can remove an online player; other players cannot', async () => {
    await assertFails(bob().ref(`sessions/${SID}/users/${ALICE}`).remove());
    await assertSucceeds(alice().ref(`sessions/${SID}/users/${BOB}`).remove());
  });

  test('the host cannot edit another player\'s stats', async () => {
    await assertFails(alice().ref(`sessions/${SID}/users/${BOB}/life`).set(1));
  });
});

describe('commander damage (#86)', () => {
  const seatOf = uid => `sessions/${SID}/users/${uid}`;

  test('is one counter on the player\'s own seat', async () => {
    await assertSucceeds(alice().ref(`${seatOf(ALICE)}/customStats/commander`).set(7));
    await assertFails(bob().ref(`${seatOf(ALICE)}/customStats/commander`).set(0));
  });

  test('the old per-opponent field is gone', async () => {
    await assertFails(alice().ref(`${seatOf(ALICE)}/commanderDamage/${BOB}`).set(7));
  });
});

describe('friends', () => {
  const req = (to, from) => `sessions/${SID}/friendRequests/${to}/${from}`;
  const acc = (to, from) => `sessions/${SID}/friendAccepted/${to}/${from}`;

  test('a player can send a request in their own name', async () => {
    await assertSucceeds(alice().ref(req(BOB, ALICE)).set({ displayName: 'Alice' }));
  });

  test('nobody can send a request in someone else\'s name', async () => {
    await assertFails(stranger().ref(req(BOB, ALICE)).set({ displayName: 'Alice' }));
  });

  test('only the recipient can dismiss a request', async () => {
    await seed({ [req(BOB, ALICE)]: { displayName: 'Alice' } });
    await assertFails(stranger().ref(req(BOB, ALICE)).remove());
    await assertSucceeds(bob().ref(req(BOB, ALICE)).remove());
  });

  test('an acceptance cannot be forged', async () => {
    // Alice pretending Bob accepted her request
    await assertFails(alice().ref(acc(ALICE, BOB)).set({ displayName: 'Bob' }));
    await assertSucceeds(bob().ref(acc(ALICE, BOB)).set({ displayName: 'Bob' }));
  });

  test('only the original sender can acknowledge an acceptance', async () => {
    await seed({ [acc(ALICE, BOB)]: { displayName: 'Bob' } });
    await assertFails(stranger().ref(acc(ALICE, BOB)).remove());
    await assertSucceeds(alice().ref(acc(ALICE, BOB)).remove());
  });
});

describe('presence', () => {
  test('a player sets and clears only their own presence', async () => {
    await assertSucceeds(alice().ref(`presence/${ALICE}`).set(SID));
    await assertSucceeds(alice().ref(`presence/${ALICE}`).remove());
    await assertFails(bob().ref(`presence/${ALICE}`).set('elsewhere'));
  });
});

describe('everything else', () => {
  test('unknown top-level paths are closed', async () => {
    await assertFails(alice().ref('admin').set(true));
    await assertFails(alice().ref('admin').get());
  });
});
