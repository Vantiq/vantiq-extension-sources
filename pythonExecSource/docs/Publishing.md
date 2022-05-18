# Publishing the Vantiq Python Execution Source

The Vantiq Python Execution Source is configured to use the standard Python 
[`setuptools`](https://pypi.org/project/setuptools/) package for building distributions,
and [`twine`](https://pypi.org/project/twine/) for publishing to a Python Package Index. 
[`Gradle`](https://gradle.org) is used to initiate this action.

## Configuration

Before publishing the connector, you must determine the destination to which you wish to publish 
(see the next section) and the version to publish. The versions used are governed by the rules 
outlined in [PEP 440 – Version Identification and Dependency Specification](https://peps.python.org/pep-0440/).
We generally use the major, minor, and micro components components (_major.minor.micro_).
See the [specification](https://peps.python.org/pep-0440/) (specifically the 
[Final Releases](https://peps.python.org/pep-0440/#final-releases) section) for details.

To set this version, set the `gradle` property `pyExecSrcVersion` to the value desired. 
This is generally done via the command line `-P` flag (_e.g._, `-PpyExecSrcVersion=1.2.3`) but
could be done using the `gradle.properties` file.

Note that the Vantiq Python Execution Source makes use of the Vantiq SDK
for Python and the Vantiq ConnectorSDK for Python.  The versions of these must be configured as well.
These _requirements_ should be chosen by editing the `vantiqSdkVersion` and `vantiqConnectorSdkVersion`
properties in the `build.gradle` file.

The versions chosen will be used to generate the `setup.cfg` file used by `setuptools`. 
They will also be used to generate a `requirements.txt` file (possibly to be used by 
`pip` for installation of required packages for development) 
and the `Dockerfile` used to generate a docker image.

## Setting up the Publishing Environment

To set up the environment to publish, you will need an account on the Python Package Index server(s) 
to which you intend to publish. To configure this access, 
\set up your `.pypirc` file as described 
[here](https://packaging.python.org/en/latest/specifications/pypirc/).

By default, the `gradle` files are configured to publish to a repository named `testpypi` 
as named in the `.pypirc` file. To publish to a different repository, add the `gradle` property 
`pypi_repo` to the command line.  For example, to publish version _1.2.3_ to a repo named _myPypiRepo_, 
use the command

```shell
    gradle -PpyExecSrcVersion=1.2.3 -Ppypi_repo=myPypiRepo publish
```

This assumes that your `.pypirc` file contains at least the following:

```
[distutils]
index-servers =
    myPypiRepo

[myPypiRepo]
repository = <url to which to publish for this repo>
username = <user name as appropriate for the repo>
password = <access token or password>
```


## References

* Gradle: [https://gradle.org](https://gradle.org)
* Setup Tools: [https://pypi.org/project/setuptools/](https://pypi.org/project/setuptools/)
* Twine: [https://pypi.org/project/twine/](https://pypi.org/project/twine/)

