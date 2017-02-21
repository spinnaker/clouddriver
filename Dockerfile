FROM java:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN GRADLE_USER_HOME=cache ./gradlew buildDeb -x test && \
  dpkg -i ./clouddriver-web/build/distributions/*.deb && \
  cd .. && \
  rm -rf workdir

RUN apt-get -y update && \
   apt-get -y install python2.7 wget unzip ca-certificates && \
   wget -nv https://dl.google.com/dl/cloudsdk/release/google-cloud-sdk.zip && \
   mkdir -p /builder && \
   unzip -qq google-cloud-sdk.zip -d /builder && \
   rm google-cloud-sdk.zip && \
   CLOUDSDK_PYTHON="python2.7" /builder/google-cloud-sdk/install.sh --usage-reporting=false \
       --bash-completion=false \
       --disable-installation-options && \
   rm -rf ~/.config/gcloud

ENV PATH "/usr/local/gcloud/google-cloud-sdk/bin:$PATH"

CMD ["/opt/clouddriver/bin/clouddriver"]
