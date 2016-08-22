# The Deployer

[![Join the chat at https://gitter.im/t1/deployer](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/t1/deployer?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Download](https://api.bintray.com/packages/t1/javaee-helpers/deployer/images/download.svg)](https://bintray.com/t1/javaee-helpers/deployer/_latestVersion)

Simple [Infrastructure As Code](http://martinfowler.com/bliki/InfrastructureAsCode.html) solution
for Java EE containers (currently only JBoss 7+) pulling from a maven repository
(currently full support only for Maven Central and Artifactory Pro, as we need to be able to search by checksum).


## Motivation

There are very good IaC solutions capable of deploying applications to and configuring resources in JEE containers.
[Ansible](http://docs.ansible.com/ansible/jboss_module.html),
[Salt](https://docs.saltstack.com/en/latest/ref/states/all/salt.states.jboss7.html), and
[Puppet](https://forge.puppet.com/biemond/wildfly) (to name just a few) all have good support for Wildfly.
They can do much more than just this, they can even install the JBoss itself.
And that's exactly the problem I had: Great power comes with great responsibility.
Too much power for me as a simple developer and application operator.
As a JEE developer, it also takes some time and dedication to learn those tools... and even more so to add to them.
I'd rather have a very limited tool, that does only what I want it to do... living in the environment I know best: JEE.
And this simplicity also brings some security, I suppose.
I don't have any central instance that can mess with any aspect of my system... rather a honey pot.

The Deployer instead tries to keep away from container specifics, so the configuration files you'll write should run
on any JEE container, given that The Deployer supports it. I must admit that this is currently quite esoteric, but I'm
trying to keep that in mind.


## 1-Minute-Tutorial

- Create a file `$JBOSS_CONFIG_DIR/deployer.root.bundle` containing:

```yaml
deployments:
  jolokia:
    groupId: org.jolokia
    artifact-id: jolokia-war
    version: 1.3.2
```

- Deploy the `deployer.war` to your container.
On startup, it will find your file, pull jolokia from maven central, and deploy it to the container.
If there is already a different version of jolokia deployed, it will replace it.

- Change the file to version `1.3.3` and the deployer will pick up the change and upgrade jolokia.

## Full Tutorial

You can find an introduction to the concepts and best practices [here](docs/tutorial.md).

## Reference

For a comprehensive list of all details, see [here](docs/reference.md).

## Versioning

We use [Semantic Versioning](http://semver.org).

Version 1.0.0 (which was never released) provided a rest api and html gui to manage deployments by remote requests or manually on each node.
As this wasn't secure / didn't scale for many instances and stages, 2.0 was initiated.

## Building

Just run `mvn clean package`.
 
If you want to run the integration tests (`*IT`) as well, run `mvn clean install`,
but the `DeployerIT` and the `ArtifactoryRepositoryIT` require some versions of jolokia and postgresql in your local maven repository
and a checksum index file generated by the `index` command in the `ArtifactoryMockLauncher`.
They may be configured to use a real MavenCentral and/or locally running Artifactory by changing constants in the test.

## Contribute

You can [join the chat](https://gitter.im/t1/deployer) or feel free to simply open issues. Pull requests are also welcome.

License: [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
