import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';

const ALICE = '11111111-1111-4111-8111-111111111111';
const BOB   = '22222222-2222-4222-8222-222222222222';
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
    userAuth: { [ALICE]: 'auth-alice', [BOB]: 'auth-bob' },
    sessionCodes: { ABCDEFGH: SID },
    sessions: {
      [SID]: {
        code: 'ABCDEFGH',
        createdAt: Date.now(),
        users: {
          [ALICE]: { displayName: 'Alice', life: 20, online: true },
          [BOB]:   { displayName: 'Bob',   life: 20, online: true },
        },
      },
    },
  }));
});

const alice    = () => env.authenticatedContext('auth-alice').database();
const bob      = () => env.authenticatedContext('auth-bob').database();
const stranger = () => env.authenticatedContext('auth-stranger').database();
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

describe('player identity (#55)', () => {
  const NEW = '33333333-3333-4333-8333-333333333333';

  test('a sign-in can claim a free player ID for itself', async () => {
    await assertSucceeds(stranger().ref(`userAuth/${NEW}`).set('auth-stranger'));
  });

  test('a sign-in cannot claim an ID for someone else', async () => {
    await assertFails(stranger().ref(`userAuth/${NEW}`).set('auth-alice'));
  });

  test('re-claiming your own ID is a harmless no-op', async () => {
    await assertSucceeds(alice().ref(`userAuth/${ALICE}`).set('auth-alice'));
  });

  test('a claimed ID cannot be released', async () => {
    await assertFails(alice().ref(`userAuth/${ALICE}`).remove());
  });

  test('a claimed ID cannot be taken over', async () => {
    await assertFails(stranger().ref(`userAuth/${ALICE}`).set('auth-stranger'));
  });

  test('player IDs must be UUIDs', async () => {
    await assertFails(stranger().ref('userAuth/not-a-uuid').set('auth-stranger'));
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
    const NEW = '33333333-3333-4333-8333-333333333333';
    await seed({ [`userAuth/${NEW}`]: 'auth-stranger' });
    await assertFails(stranger().ref(`${seat(NEW)}/online`).set(false));
    await assertSucceeds(stranger().ref(seat(NEW)).set({ displayName: 'Carol', life: 40, online: true }));
  });

  test('unknown fields and oversized values are rejected', async () => {
    await assertFails(alice().ref(`${seat(ALICE)}/isAdmin`).set(true));
    await assertFails(alice().ref(`${seat(ALICE)}/displayName`).set('x'.repeat(65)));
    await assertFails(alice().ref(`${seat(ALICE)}/displayName`).set(''));
    await assertFails(alice().ref(`${seat(ALICE)}/life`).set(-1));
    await assertFails(alice().ref(`${seat(ALICE)}/life`).set('20'));
  });
});

describe('stat names (#57)', () => {
  const def = name => alice().ref(`sessions/${SID}/customStatNames/${name}`);

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

  test('a session with players in it cannot be deleted or replaced', async () => {
    await assertFails(alice().ref(`sessions/${SID}`).remove());
    await assertFails(alice().ref(`sessions/${SID}`).set({ code: 'NEWCODE2', createdAt: { '.sv': 'timestamp' } }));
    await assertFails(alice().ref(`sessions/${SID}/code`).set('NEWCODE2'));
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
