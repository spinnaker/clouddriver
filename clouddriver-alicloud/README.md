## Alibaba Cloud Clouddriver 

The clouddriver-alicloud module aims to deploy an application on Alibaba Cloud.

It is a work in progress

### Configuring clouddriver.yml

```yaml
alicloud:
  enabled: true
  accounts:
    - name: aksk_test
      accessKeyId: <accessKeyId>
      accessSecretKey: <accessSecretKey>
      regions:
        - cn-hangzhou
        - cn-shanghai
    - name: assumeRole_test
      accountId: <managed accountid>
      assumeRole: role/spinnakermanaged
      regions:
        - cn-beijing  
  defaultRegion: cn-hangzhou
  accessKeyId: <managing accesskey>
  accessSecretKey: <managing accessSecretKey>
```