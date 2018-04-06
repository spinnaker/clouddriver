#!/bin/bash
# This script will build the project.

GRADLE="./gradlew --no-daemon --max-workers=1"
export GRADLE_OPTS="-Xmx1g -Xms1g"

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  $GRADLE -Prelease.useLastTag=true -Pskip.loadtest=true build
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  $GRADLE -Prelease.travisci=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -Pskip.loadtest=true -x test build snapshot --stacktrace
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  case "$TRAVIS_TAG" in
  version-*)
    ;; # Ignore Spinnaker product release tags.
  *-rc\.*)
    $GRADLE -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -Pskip.loadtest=true -x test candidate --stacktrace
    ;;
  *)
    $GRADLE -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -Pskip.loadtest=true -x test final --stacktrace
    ;;
  esac
else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  $GRADLE -Prelease.useLastTag=true -Pskip.loadtest=true build
fi

