from twitter.common import log

from twitter.aurora.client.api import AuroraClientAPI
from twitter.aurora.client.base import check_and_log_response, deprecation_warning, die
from twitter.aurora.common.aurora_job_key import AuroraJobKey


class LiveJobDisambiguator(object):
  """
  Disambiguates a job-specification into concrete AuroraJobKeys by querying the scheduler API.
  """

  def __init__(self, client, role, env, name):
    if not isinstance(client, AuroraClientAPI):
      raise TypeError("client must be a AuroraClientAPI")
    self._client = client

    if not role:
      raise ValueError("role is required")
    self._role = role
    if not name:
      raise ValueError("name is required")
    self._name = name
    self._env = env

  @property
  def ambiguous(self):
    return not all((self._role, self._env, self._name))

  def query_matches(self):
    resp = self._client.get_jobs(self._role)
    check_and_log_response(resp)
    return set(AuroraJobKey(self._client.cluster.name, j.key.role, j.key.environment, j.key.name)
        for j in resp.result.getJobsResult.configs if j.key.name == self._name)

  @classmethod
  def _disambiguate_or_die(cls, client, role, env, name):
    # Returns a single AuroraJobKey if one can be found given the args, potentially
    # querying the scheduler. Calls die() with an appropriate error message otherwise.
    try:
      disambiguator = cls(client, role, env, name)
    except ValueError as e:
      die(e)

    if not disambiguator.ambiguous:
      return AuroraJobKey(client.cluster.name, role, env, name)

    deprecation_warning("Job ambiguously specified - querying the scheduler to disambiguate")
    matches = disambiguator.query_matches()
    if len(matches) == 1:
      (match,) = matches
      log.info("Found job %s" % match)
      return match
    elif len(matches) == 0:
      die("No jobs found")
    else:
      die("Multiple jobs match (%s) - disambiguate by using the CLUSTER/ROLE/ENV/NAME form"
          % ",".join(str(m) for m in matches))

  @classmethod
  def disambiguate_args_or_die(cls, args, options, client_factory=AuroraClientAPI):
    """
    Returns a (AuroraClientAPI, AuroraJobKey, AuroraConfigFile:str) tuple
    if one can be found given the args, potentially querying the scheduler with the returned client.
    Calls die() with an appropriate error message otherwise.

    Arguments:
      args: args from app command invocation.
      options: options from app command invocation. must have env and cluster attributes.
      client_factory: a callable (cluster) -> AuroraClientAPI.
    """
    if not len(args) > 0:
      die('job path is required')
    try:
      job_key = AuroraJobKey.from_path(args[0])
      client = client_factory(job_key.cluster)
      config_file = args[1] if len(args) > 1 else None  # the config for hooks
      return client, job_key, config_file
    except AuroraJobKey.Error:
      log.warning("Failed to parse job path, falling back to compatibility mode")
      role = args[0] if len(args) > 0 else None
      name = args[1] if len(args) > 1 else None
      env = None
      config_file = None  # deprecated form does not support hooks functionality
      cluster = options.cluster
      if not cluster:
        die('cluster is required')
      client = client_factory(cluster)
      return client, cls._disambiguate_or_die(client, role, env, name), config_file
