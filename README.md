# saker.build.ide.eclipse

![Build status](https://img.shields.io/azure-devops/build/sakerbuild/53444128-aeb0-4d50-8b42-1ff5c679b47e/22/master)

The Eclipse IDE plugin for the [saker.build system](https://saker.build). The plugin embeds the saker.build runtime and provides support for invoking it from the IDE.

See the [installation guide](https://saker.build/saker.build/doc/eclipseplugin.html) to get started.

## Features

* Invoking the build with saker.build
* Script language support
	* Content assist
	* Outline
	* Build task and related documentation available in the script editor
* Configuring the Eclipse projects based on the build execution
	* E.g. applying Java nature for the project if the build compiles Java sources
* Environment feature discovery via extension plugins
	* E.g. discovering the installed Java runtimes and passing it as a configuration to the build

## Build instructions

The project can be built by importing it into Eclipse.

## License

The source code for the project is licensed under *GNU General Public License v3.0 only*.

Short identifier: [`GPL-3.0-only`](https://spdx.org/licenses/GPL-3.0-only.html).

Official releases of the project (and parts of it) may be licensed under different terms. See the particular releases for more information.
