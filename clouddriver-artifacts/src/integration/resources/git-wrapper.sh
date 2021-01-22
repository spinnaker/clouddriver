#!/bin/bash

#-------------------------------------------------------------------------------------
# Wraps all git binary calls to the git binary inside gitea container,
# transferring any needed files and adjusting file paths between host and container.
#-------------------------------------------------------------------------------------

set -e

echo "$@" >> /tmp/git-log

git_args=$@

# Change local paths for paths inside the container, and copy any needed files
function pre_process() {
  container_name=$(docker ps | grep "gitea/gitea" | awk '{print $1}')
  if [[ $1 == "clone" ]]; then
    docker exec "$container_name" rm -rf test
  elif [[ $1 == "archive" ]]; then
    {
      git_args="-C test "
      while [[ "$#" -gt 0 ]]; do
        case $1 in
        --output)
          git_args+="--output test.tgz "
          dst_path=$2
          shift
          ;;
        *) git_args+="$1 " ;;
        esac
        shift
      done
    } >/dev/null 2>&1
  fi

  docker exec -i "$container_name" mkdir -p "$SSH_KEYS"
  docker cp "${SSH_KEYS}/known_hosts" "$container_name":"${SSH_KEYS}/known_hosts"
  docker cp "${SSH_KEYS}/id_rsa_test" "$container_name":"${SSH_KEYS}/id_rsa_test"
  docker exec -i "$container_name" chmod 600 "${SSH_KEYS}/id_rsa_test"

  if [[ -n "${SSH_ASKPASS}" ]] ; then
    docker exec -i "$container_name" mkdir -p "$(dirname "$SSH_ASKPASS")"
    docker cp "${SSH_ASKPASS}" "$container_name":"${SSH_ASKPASS}"
    docker exec -i "$container_name" chmod +x "${SSH_ASKPASS}"
  fi
}

function execute() {
  echo "docker exec -i $container_name git $git_args" >> /tmp/git-log
  echo "GIT_SSH_COMMAND: $GIT_SSH_COMMAND, SSH_ASKPASS: $SSH_ASKPASS SSH_KEY_PWD: $SSH_KEY_PWD DISPLAY: $DISPLAY" >> /tmp/git-log
  docker exec -i -e GIT_SSH_COMMAND -e SSH_ASKPASS -e SSH_KEY_PWD -e DISPLAY -e GIT_CURL_VERBOSE -e GIT_TRACE "$container_name" git $git_args
}

# Make any changes locally reflecting the changes done inside the container
function post_process() {
  if [[ $git_args =~ .*clone.* ]]; then
    mkdir -p test
  elif [[ $git_args =~ .*archive.* ]]; then
    docker cp "$container_name":/test/test.tgz "$dst_path"
  fi
}

pre_process $@
execute
post_process
