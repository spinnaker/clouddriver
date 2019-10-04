#!/bin/bash
# This script will build the project.

GRADLE="./gradlew -PenablePublishing=true --no-daemon --max-workers=1"
GRADLE_3="./gradlew -PenablePublishing=true --no-daemon --max-workers=3"
export GRADLE_OPTS="-Xmx1g -Xms1g"

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  $GRADLE build javadoc
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  $GRADLE -Prelease.travisci=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -x test build snapshot --stacktrace
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  case "$TRAVIS_TAG" in
  version-*)
    ;; # Ignore Spinnaker product release tags.
  *-rc\.*)
    $GRADLE -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -x test -x check candidate --stacktrace
    ;;
  *)
    $GRADLE_3 -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -x test -x check -x javadoc -x publishBuildDeb final --stacktrace
    ;;
  esac
else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  $GRADLE build
fi

