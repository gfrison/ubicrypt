# UbiCrypt

## Keep your digital life in your control. Privately and easily

_Cloud storage is pervasive and cheap, but it raises concerns on privacy, security, resiliantness and portability._

*UbiCrypt* is a desktop application that allows you to keep your data secret and safely stored in multiple locations.

[![Build Status](https://travis-ci.org/gfrison/ubicrypt.svg?branch=master)](https://travis-ci.org/gfrison/ubicrypt)  
[![Join the chat at https://gitter.im/ubicryp/Lobby](https://badges.gitter.im/ubicryp/Lobby.svg)](https://gitter.im/ubicryp/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

### Ubicrypt encrypts your files and keeps them to any cloud storage
#### Key features:
  - Asymetric encryption with [Elliptic Curves 32bit](https://en.wikipedia.org/wiki/Elliptic_curve).
  - Symmetric encryption with [GCM-AES-256](https://en.wikipedia.org/wiki/Galois/Counter_Mode).
  - File replication on multiple [Cloud Storage](https://en.wikipedia.org/wiki/Cloud_storage) system.
  - Sharing between multiple devices (Computer at home, at work, etc)
  - File Versioning and synchronization with [Vector Clocks](https://en.wikipedia.org/wiki/Vector_clock)

#### Software Stack:
  - [BouncyCastle](https://www.bouncycastle.org/). PGP/AES Encryption.
  - [Spring Boot](https://projects.spring.io/spring-boot/). Application container.
  - [RxJava](https://github.com/ReactiveX/RxJava). Asyncronous and event-based framework.
  - [JavaFX](http://docs.oracle.com/javase/8/javase-clienttechnologies.htm). Java UI library

#### Run UbiCrypt 

Prerequisites:

  - Java JDK 8
  - If OpenJDK installed, javafx should be [installed separately](http://chriswhocodes.com/).

`./gradlew run`

#### License

Copyright Giancarlo Frison.

Licensed under the [UbiCrypt License](LICENSE.md); you may not use this file except in compliance with the License. 

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
