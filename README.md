# Deployer

[![Join the chat at https://gitter.im/t1/deployer](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/t1/deployer?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Download](https://api.bintray.com/packages/t1/javaee-helpers/deployer/images/download.svg)](https://bintray.com/t1/javaee-helpers/deployer/_latestVersion)

Simple [Infrastructure As Code](http://martinfowler.com/bliki/InfrastructureAsCode.html) solution for Java EE containers (currently only JBoss 7+) pulling from a maven repository (currently full support only for Maven Central and Artifactory, as we need to be able to search by checksum).

## 1-Minute-Tutorial

- Create a file `$JBOSS_CONFIG_DIR/deployer.root.bundle` containing:

```yaml
artifacts:
  jolokia-war:
    groupId: org.jolokia
    version: 1.3.2
```

- Deploy the `deployer.war` to your container.
On startup, it will find your file, pull jolokia from maven central, and deploy it to the container.
If there is already a different version of jolokia deployed, it will replace it.

- Change the file to version `1.3.3` and the deployer will pick up the change and upgrade jolokia.

## Reference

### Deployments
### LogHandlers
### Loggers


## History

Version 1.0.0 (which was never released) provided a rest api and html gui to manage deployments manually on each node.
As this didn't scale for many instances and stages, 2.0 was initiated.
