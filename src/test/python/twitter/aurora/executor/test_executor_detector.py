import itertools
import os

from twitter.aurora.executor.executor_detector import ExecutorDetector
from twitter.common.contextutil import temporary_dir
from twitter.common.dirutil import safe_mkdir


class Match(object):
  def __init__(self, root, slave, framework, executor, run):
    self._groups = dict(
        root=root, slave_id=slave, framework_id=framework, executor_id=executor, run=run)

  def groups(self):
    return self._groups


DEFAULT_MATCH = Match('abcd', 'slave', 'framework', 'executor', 'run')


def test_find_root():
  BAD_PATHS = (
    os.path.sep,
    '.',
    os.path.sep * 10,
    '/root/slaves',
    '/root/slaves/S/frameworks/F/executors//runs/R',
    'root/slaves/S/frameworks/F/executors//runs/R',
  )

  GOOD_PATHS = (
    ExecutorDetector.path(DEFAULT_MATCH),
    os.path.join(ExecutorDetector.path(DEFAULT_MATCH), 'some', 'other', 'path')
  )

  for cwd in BAD_PATHS:
    assert ExecutorDetector.find_root(cwd) is None

  for cwd in GOOD_PATHS:
    assert ExecutorDetector.find_root(cwd) == 'abcd'


def test_match_inverse():
  assert ExecutorDetector.match(ExecutorDetector.path(DEFAULT_MATCH)).groups() == (
      DEFAULT_MATCH.groups())


def test_bad_match():
  assert ExecutorDetector.match('herpderp') is None


def test_integration():
  SLAVES = ('slave001', 'slave123')
  FRAMEWORKS = ('framework1', 'framework2')
  EXECUTORS = ('executor_a', 'executor_b')
  RUNS = ('001', '002', 'latest')

  with temporary_dir() as td:
    all_groups = set()
    for slave, framework, executor, run in itertools.product(SLAVES, FRAMEWORKS, EXECUTORS, RUNS):
      match = Match(td, slave, framework, executor, run)
      safe_mkdir(ExecutorDetector.path(match))
      all_groups.add(tuple(sorted(match.groups().items())))

    for match in ExecutorDetector(td):
      assert tuple(sorted(match.groups().items())) in all_groups
