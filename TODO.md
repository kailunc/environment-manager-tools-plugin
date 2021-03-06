TODO list for the environment manager integration project
========================================================

1. Improvements in the client
-----------------------------

1.1. The client currently receives and returns JSON objects.  This should be
abstracted, and handwritten classes should be created.  For instance, instead
of returning a JSON object for the Provisions#createProvisionEvent(...) method.
It should return an Event object, which contains the relevant fields and an 
array of Step objects.

1.2. The client needs to be completed.  Initial implementation only implemented
the API methods needed for the initial jenkins integration.  Missing methods
should be added for completeness.

1.3. The client needs to be versioned.  Currently it will only work for V1 of
the environment manager API.  When subsequent versions are released, it will
need to be updated for those versions. But in order to maintain backward
compatibility, multiple versions of the client will be needed.


2. Password encryption
----------------------

The jenkins plugin already encrypts user passwords using an already existing
jenkins framework.  However the ant and maven plugins do not do any sort of
password encryption.

Here are the options for ant and maven, as I see them:

2.1. Do nothing.  Users might be ok with clear text passwords in build scripts,
often we configure a system with a generic "build" users which can be used for
these purposes.

2.2. [Desired per ML] Use the concerto encryptions mechanism.  Concerto has a 
hidden page which will encrypt passwords.  This encrypted password can then be
used in the maven or ant build script.  This will require reusing some code 
that is currently in concerto to do the decryption.  I'm not sure how reusable
this code is at the moment.  May require some refactoring and/or cooperation 
from the Concerto team. On the other hand, they are building with maven now, so 
there may already be a module available.

2.3. Maven does have a concept of encrypted server passwords.  I briefly looked
into using this mechanism for maven.  It's not completely straight forward but
it does seem achievable.


3. Unit testing
---------------

3.1. Unit tests on the client, are not very comprehensive.  They only do basic
assertions and were more used for manually examining the output of the EM API.
These should obviously be beefed up.

3.2. Unit tests are needed for the jenkins plugin.  The plugin has some default
tests that were created by the maven archetype.  We need to add tests here that
will test our functionality.  I have not done any research into how to add to
these unit tests.

See https://wiki.jenkins-ci.org/display/JENKINS/Unit+Test for help.


4. Misc
-------

4.1 Jenkins trend graph
4.2 Localization
