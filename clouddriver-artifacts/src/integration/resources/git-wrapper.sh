#!/bin/bash

#------------------------------------------------------------------------------
# Wraps all git binary calls to the git binary inside gitea container.
#------------------------------------------------------------------------------

set -e

echo "$@" >> /tmp/git-wrapper-log

git_args=$@

# Change local paths for paths inside the container
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
}

function execute() {
  docker exec -i "$container_name" git $git_args
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
