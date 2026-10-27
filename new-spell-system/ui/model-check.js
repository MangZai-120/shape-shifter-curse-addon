'use strict';
const assert = require('node:assert/strict');
const M = require('./model.js');
const base = M.normalise({ core: 'thermal', carrier: 'projectile', structure: 'plain', form: 'human', intensity: 1, prep: 40, range: 32, burden: 70 });
assert.equal(M.quote(base).actual, 16.2);
for (const form of Object.keys(M.forms)) {
  const s = { ...base, form };
  assert.equal(M.risk(s, M.forms[form].safety).failure, 0);
  assert.equal(M.risk(s, M.forms[form].safety).offset, 0);
}
const q = M.quote(base);
assert.equal(q.direct + q.splash, q.budget);
assert.equal(M.quote({ ...base, structure: 'split' }).direct * 3, q.budget);
assert.equal(M.quote({ ...base, structure: 'merge' }).direct, q.budget);
assert.ok(M.quote({ ...base, prep: 80 }).actual < q.actual);
assert.ok(M.quote({ ...base, prep: 0 }).actual > q.actual);
assert.ok(M.risk(base, 119).failure > M.risk(base, 102).failure);
assert.ok(M.risk({ ...base, intensity: 2 }, 110).failure > M.risk(base, 110).failure);
assert.notEqual(M.risk(base, 110).failure, M.risk({ ...base, form: 'familiar' }, 210).failure);
assert.deepEqual(M.plan(base).checkpoints.map(p => p.tick), [0, 10, 20, 30, 40]);
assert.deepEqual(M.plan({ ...base, prep: 35 }).checkpoints.map(p => p.tick), [0, 10, 20, 30]);
assert.equal(M.plan({ ...base, prep: 0 }).checkpoints.length, 1);
const immediate = M.simulate({ ...base, burden: 500, prep: 0 }, 1);
assert.equal(immediate.status, 'failed');
assert.equal(immediate.paid, immediate.quote.actual);
const initialFailure = M.simulate({ ...base, burden: 500 }, 1);
assert.equal(initialFailure.status, 'failed');
assert.equal(initialFailure.paid, 0);
assert.equal(initialFailure.endTick, 0);
const safe = M.simulate({ ...base, burden: 0 }, 1);
assert.equal(safe.status, 'complete');
assert.equal(safe.paid, safe.quote.actual);
assert.equal(safe.yaw, 0);
assert.equal(safe.pitch, 0);
for (const structure of Object.keys(M.structures)) {
  const normal = M.quote({ ...base, structure }), trained = M.quote({ ...base, structure, mastery: 1 });
  assert.ok(Math.abs(trained.actual / normal.actual - trained.floor) < 1e-12);
}
assert.ok(M.validate({ ...base, core: 'space', carrier: 'projectile' }).length);
assert.ok(M.validate({ ...base, core: 'lunar', carrier: 'self', structure: 'split' }).length);
let partialFailure;
for (let seed = 1; seed < 1000; seed++) {
  const attempt = M.simulate({ ...base, burden: 110, intensity: 2 }, seed);
  if (attempt.status === 'failed' && attempt.endTick > 0 && attempt.endTick < base.prep) { partialFailure = attempt; break; }
}
assert.ok(partialFailure);
assert.ok(partialFailure.paid > 0 && partialFailure.paid < partialFailure.quote.actual);
console.log('Design model checks passed: budget conservation, typed structures, live form risks, timeline boundaries, partial payment, adaptation and safe-area guarantees.');
console.log('This verifies the design calculator only; Minecraft gameplay is not implemented by this prototype.');
