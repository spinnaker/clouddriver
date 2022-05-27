## Alibaba Cloud Clouddriver 

The clouddriver-alicloud module aims to deploy an application on Alibaba Cloud.

It is a work in progress

### Configuring clouddriver.yml

```yaml
alicloud:
  enabled: true
  accounts:
    - name: test1
      accessKeyId: accessKeyId
      accessSecretKey: accessSecretKey
      regions:
        - cn-hangzhou
        - cn-shanghai
    - name: test2
      accessKeyId: accessKeyId
      accessSecretKey: accessSecretKey
      regions:
        - cn-hangzhou
        - cn-shanghai

```