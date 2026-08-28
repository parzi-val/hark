// Runnable self-check for the merge engine (data-loss critical). Not imported by
// the app, not bundled. Run:  node src/sync/merge.selfcheck.ts   (from web/)
import { mergeRecords } from './merge.ts';

declare const process: { exit(code: number): never };

function assert(cond: boolean, msg: string) {
  if (!cond) {
    console.error('FAIL:', msg);
    process.exit(1);
  }
}

// newer remote overwrites older local
{
  const m = mergeRecords(
    [{ uid: 'a', updatedAt: 10, v: 'local' }],
    [{ uid: 'a', updatedAt: 20, v: 'remote' }],
  );
  assert(m.length === 1 && m[0].v === 'remote', 'newer remote wins');
}

// older remote does NOT clobber newer local
{
  const m = mergeRecords(
    [{ uid: 'a', updatedAt: 30, v: 'local' }],
    [{ uid: 'a', updatedAt: 20, v: 'remote' }],
  );
  assert(m[0].v === 'local', 'older remote loses');
}

// a newer delete (tombstone) propagates over an older edit
{
  const m = mergeRecords(
    [{ uid: 'a', updatedAt: 10, deleted: false }],
    [{ uid: 'a', updatedAt: 20, deleted: true }],
  );
  assert(m[0].deleted === true, 'newer delete propagates');
}

// a newer edit wins over an older delete (undelete / resurrection by edit)
{
  const m = mergeRecords(
    [{ uid: 'a', updatedAt: 30, deleted: false }],
    [{ uid: 'a', updatedAt: 20, deleted: true }],
  );
  assert(m[0].deleted === false, 'newer edit beats older delete');
}

// records on only one side are kept (union)
{
  const m = mergeRecords([{ uid: 'a', updatedAt: 10 }], [{ uid: 'b', updatedAt: 10 }]);
  assert(m.length === 2, 'union of both sides');
}

// idempotent: merging the same remote twice is stable
{
  const remote = [{ uid: 'a', updatedAt: 20, v: 'y' }];
  const once = mergeRecords([{ uid: 'a', updatedAt: 10, v: 'x' }], remote);
  const twice = mergeRecords(once, remote);
  assert(twice.length === 1 && twice[0].v === 'y', 'idempotent');
}

console.log('merge self-check: all passed');
