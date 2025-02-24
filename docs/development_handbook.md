Developer handbook
==================

Table of contents
-----------------

- [New Developer Setup](#new-developer-setup)
- [JDK](#jdk)  
- [Guiding ideas](#guiding-ideas)
- [Adding a completely new endpoint](#adding-a-completely-new-endpoint)  
- [Version](#version)
- [Threads](#threads)
- [Types](#types)
- [Behavior-Driven Development (BDD)](#behavior-driven-development-bdd)
- [Test Helpers](#test-helpers)
- [Performance](#performance)
- [Wrapping exceptions](#wrapping-exceptions)
- [Invariants](#invariants)
- [General architecture](#general-architecture)
- [Persistence files](#persistence-files)
- [Database](#database)  
- [Authentication](#authentication)
- [Timeouts and sessions](#timeouts-and-sessions)
- [API](#api)
- [Parsers and clean text](#parsers-and-clean-text)
- [Web page dev and test](#web-page-development-and-testing)
- [Logging](#logging)
- [Regular expressions](#regular-expressions-regex)
- [Directories](#directories)


New Developer Setup
-------------------

Here's the general series of steps for a new developer:

1. Install Java 13 onto your machine if you don't already have it. Make sure to add the path to your JDK to your environment
   as JAVA_HOME, and also add the path to the Java binaries to your PATH variable.
2. Download and install IntelliJ Community Edition (which is free of charge) if you don't already have 
   it. This is definitely the best IDE for our purposes, since it is developed by the same company
   that develops the Kotlin language, and also, it's awesome.  And it's the IDE all the other developers use. Find it here: https://www.jetbrains.com/idea/download/
3. Obtain this source code from Github, at https://github.com/7ep/r3z.git
4. Run the tests on your computer, using the command line: gradlew test 
   (you might need to add dot-slash before that, if you are using macOS or Linux)
5. Run the application to get a feel for the user experience: gradlew run
6. Open the software with IntelliJ.  Try building and running tests with it.  Note that the
   default test runner in IntelliJ will run the tests serially (rather than in parallel) which
   takes a while.  To run them faster, find the gradle tool window and the test task within it,
   and run the tests there.
7. Read through some of this developer documentation to get a feel for some of its unique characteristics.
8. Examine some of the tests in the system to get a feel for how the system works and how
   test automation is done.
9. It may be necessary to configure your browser to allow unsigned certificates. On Chrome, that is 
   easily done: just put chrome://flags/#allow-insecure-localhost as your URL and enable it.

Optional:
* Install "Code With Me" plugin for IntelliJ - Preferences > Plugins > Code WIth me
    * Use the new "Code With Me" icon in top bar to enable "Full Access" (turn off "start voice call")
    * Share the link with your friends
    

JDK
---

We need a minimum of Java 13 so we get the latest version of TLS, which is 1.3, and also
there was a bug in TLS 1.3 that was fixed in Java 13.

Guiding ideas
-------------

1. Take care of the pennies and the pounds will take care of themselves
2. A stitch in time saves nine


Adding a completely new endpoint
--------------------------------

To better understand how development takes place, it will be helpful to imagine
creating an entirely-new endpoint.  

As an example, let's say we want to add the concept of roles.  You could be
- an administrator
- an auditor
- someone's supervisor
- someone's report

If we are following a test-driven approach to developing this, the first thing
you will want to focus on is the utility - that is, the overarching business code.

If this is something that can fit inside an existing domain - like time recording -
then it suits you to add that.  If it does not (and that might be the case for roles),
then you can make another domain at the top level, under the coverosR3z package.

Underneath that, the typical pattern for organizing the kinds of files are indicated
in [Directories](#directories).

If we are following a test-driven approach to developing this, the first thing
you will want to focus on is the utility - that is, the overarching business code.

Start by creating a test in the appropriate place in the tests directory, and
using wishful thinking, test what you wish already existed.

The utility class accesses the database through a ___Persistence file, so after you
have started testing the utility class, you may find yourself needing to mock out
some of the interaction with the database.  If you need a persistence file that does
not yet exist, you will likely need to create a new mock as well - see any of the
classes like "Fake___Persistence".

Once your utility is well-tested from both unit-test and integration approaches,
you will want to provide access to it through an endpoint.  An example of one of
the simpler endpoints can be seen at CreateEmployeeAPI.

Some key patterns to note:

The class contains an "Elements" enum class that inherits from "Element".  This allows
us to replace strings in the HTML text with constant values, giving us stronger
typing - if the string needs to change, it can be done more safely than a simple
string replacement command.

It also contains a companion object that inherits from GetEndpoint and PostEndpoint,
which requires us to implement several methods.  Those interfaces correspond to
their obvious purpose - they allow handling requests for a GET or a POST.

Using either of those interfaces will also require implementing the path property,
which is used to indicate the URL path to this endpoint.

When implementing handleGet or handlePost, it is imperative that you use some kind
of authentication mechanism.  See the existing code and the authentication
methods provided at AuthUtilities.

Version
-------

Each release gets a new version, using major and minor, like 1.0
See r3z_version in the gradle.properties file for more info.

As we change the schema of the database, update the database version.
The version of the database is called CURRENT_DATABASE_VERSION in Constants


Threads
-------

Any kind of data that could be shared between threads, like caches or databases,
needs to be made "thread-safe", which means that threads don't potentially corrupt
the data.  If no precautions are taken, it is possible for two threads to make
changes to the same data in unexpected ways.  If this is completely a new concept to you,
take the time to review some of the ideas about parallel processing, and also look at tests
in this application like testCorruptingEmployeeDataWithMultiThreading.


Types
-----

Throughout the system, there is an attempt to keep raw values (strings, ints
floats, and so on) wrapped into custom types - for example, Password.

This is for some practical reasons:

1. they are better documented as they are used in the code.
2. It prevents accidental cross-wiring, like for example: if a method could take
    a username and password string. Because they are typed - i.e. UserName("user1")
    and Password("password123"), the values are only allowed to fit in the proper slot.
3. It centralizes invariants for types.  For example, if a password must be non-empty,
   at least 12 characters and less than 255 chars, those requirements can be part of
   the initialization of the type and then never needs to be called again.


Behavior-Driven Development (BDD)
---------------------------------

BDD files will often have a suffix of "BDD".  See "EnteringTimeBDD" for a sample.

Note that there is a type in the system called "UserStory" and one called "BDDScenario",
both of which are in the test source code.

Once all the tests are complete, you will see the BDD results written to build/bdd


Test Helpers
------------

When writing tests, you might find some of the functions in TestHelpers.kt handy


Performance
-----------

Performance tests include the text "PERFORMANCE" in the name.  We are currently
running an experiment of writing the results of these tests to files in
docs/performance_archive/granular_tests , to see whether trends over time help
us think more clearly about system performance.


Wrapping exceptions
-------------------

As we code, we encounter many places where exceptions can be thrown. Often,
in the case of third-party code, the exception may be confusing, ambiguous,
too-universal, or include critical information in a overly-complex stacktrace.

To avoid the difficulty this causes during maintenance and debugging, it is
imperative we wrap potential exception throwers in our own handling code
where we can more-precisely describe the problem.

For an example, in several of the top-level packages (the domains), there is 
often an "exceptions" package that contains related custom exceptions for that domain


Invariants
----------

We hold these truths to be self-evident in our code...

The following are used in our code to positively assert
truthful statements inside algorithms.  Don't use "assert",
since that is possible to turn off, and we want these to always
run.

fun require(value: Boolean)
Throws an IllegalArgumentException if the value is false.
This is for asserting any kind of truth for an argument to a function.

fun check(value: Boolean)
Throws an IllegalStateException if the value is false.
This is for asserting any kind of truth for a section of code in an algorithm.

fun <T : Any> checkNotNull(value: T?): T
Throws an IllegalStateException if the value is null.
Otherwise returns the not null value.
This is for asserting that code within an algorithm is not null.

fun <T : Any> requireNotNull(value: T?): T
Throws an IllegalArgumentException if the value is null.
Otherwise returns the not null value.
This is for asserting that arguments to a function aren't null.

Also see our customized checks, such as checkParseToInt


General architecture
--------------------

The general architecture pattern is to have various abilities provided in files with a
suffix of "Utilities".  They, in turn, have access to a file with a suffix
of "Persistence" which controls their access to the database, and which
eventually talks to PureMemoryDatabase, where the data is actually stored.

These constitute the business application.  We then provide an interface
to this code from the outside world, like for example, by providing http
access, whose code is in files with a suffix of "API"

Keeping things clean and simple is crucial.

To stay organized, follow these guidelines:
    1) Calls from the outside world (e.g. the web) call into any of the
    "-Utilities" files.  They must not call directly to the "-Persistence"
    files or straight to the PureMemoryDatabase


Persistence files
-----------------

When dealing with this layer, some general design
considerations must be maintained.

1. The code in this layer is *solely* dedicated to CRUD details.  The only other
   logic to include is checking invariants - that is, making sure that certain
   fundamental truths are held true, for data that is readily available at this
   level.  There is no *exact* right way to do this, but in general we don't want
   general business logic occurring here.
   

Database
--------

The database is where we store all the shared state for the users.  It is 
simple. Data is of type [ChangeTrackingSet], which is a thread-safe data structure.

The Database mainly consists of the data and some utilities for access and persistence.

Note that in typical usage (where we are persisting data to disk as it is written),
the persistence takes place asynchronously.  That is, you might tell the database to
create a new user, and it might reply "done", but writing that to the disk may happen
milliseconds later.  This is because all writes to disk are put into a queue on its
own thread. It means we can just pop that request in, and trust it will be done later 
and in the right order.

See actOn() in the DataAccess class for specifics.  The queue is ActionQueue.

Authentication
--------------

When a user logs in, we set a cookie on their browser that represents the identifier
to a session in the database.  As long as they have that cookie, effectively it's like
their browser is telling us a correct password every time they come to a page.

As far as authentication goes, there are several kinds of pages.  For example, there
are
1) pages that require a valid session token to see them at all.
2) There are pages like the public homepage, registration page, and login, that should cause a user to be
redirected to an authenticated homepage if they are holding a valid session token.
3) There are pages that require both a valid session token *and* special authorization
to be on that page, like an administrator's page.  Finally, there is
4) a logout page that explicitly removes the session token cookie and deletes the session from the database.

There are three key pieces to the authentication puzzle: The token (i.e. the cookie), the session
entry in the database, and the page you are requesting.

"valid token" means a cookie that correlates to an existing current session in the database
"invalid token" means a cookie that doesn't have a correlating session

Timeouts and sessions
---------------------

A question to consider: if a user doesn't explicitly log out, does anything happen to
their session?  Does it simply remain valid indefinitely? One possibility is to "timeout"
the session - if it hasn't been accessed within some duration, it is considered "dead"
and will be removed the next time it is accessed.  On the other hand, is there any problem
with keeping sessions active indefinitely?


API
---

The HTTP endpoints have the acronym API as a suffix.

For developing an API, some crucial information:
1. Routing is set in RoutingUtilities
2. Every API has a companion object that implements an API interface, such as
   GetEndpoint or PostEndpoint (for example, see HomepageAPI.kt)
3. Choose the authentication mechanisms for the page, from coverosR3z.server.utility.AuthUtilities.Companion.
   Examine any of the other API's to see how these are used, in the handleGet and handlePost methods.


Parsers and clean text
----------------------

Sometimes, text that users input will be run through a parser - that is, a program that analyzes
the characters of the text and makes decisions based on it.  For example, when you send text to
a browser, it parses it - if text has angle brackets around it, like <this>, it is considered an HTML
element and is treated differently.

As it happens, the most prevalent security vulnerability is indeed this.  So whenever there is a
possibility that user input can get parsed, we have to clean it.  That means stripping the special
characters that designates special formations, e.g. <this> becomes &lt;this&gt; before shipping
to the client browser, using our safeHtml() or safeAttr() code.

As we deal with more parsers, we will need to include more cleaning utilities.


Web page development and testing
--------------------------------

A thought about improving the cycle-time for the web pages: Chrome has an *override* feature - it will
allow you to save a file that the browser will use instead of pulling from the server.
See https://developers.google.com/web/updates/2018/01/devtools?utm_source=devtools#overrides

This means we can develop a web page to our heart's content very quickly and easily, and once it's about
where we want, we copy the relevant portions into the real system.

Inside the <head> element of our HTML, try to follow a pattern of setting the file location for
the HTML, as we see for example in RegisterAPI at registerHTML, you will see this line:
    <meta apifile="RegisterAPI" >



Logging
-------

types of logs:

AUDIT - Business-related content.  Be very strict here about what constitutes business.

DEBUG - Less-relevant business content and the large bulk of technical logging

TRACE - highly-verbose log entries that are only useful when debugging

WARN - a warning to operators - something happened that needed resolving, the application
       probably made the right decision but it's something to be aware of.  For example,
       if we start and cannot find any time entries on the disk, we'll create an empty
       data structure for the time entries.  That's probably the right path, but if
       something went haywire on the disk, if files got accidentally moved around, that
       might not be the right choice.  Unfortunately, computers are stupid and there
       has to be a balance trying to accommodate literally every imaginable thing
       that could ever happen (which is impossible to program).

IMPERATIVE - these are log entries that *must* show, no matter what.  Rare but valuable,
             for example it is used during startup and shutdown of the application, and
             also when we change the log settings dynamically during runtime


notes:
1. In the log API, there are some that expect an input to be a lambda, like logTrace(msg: () -> String)
   That is a technique so anything we pass in to the function won't be actually run unless
   the log level is used.  So for example, if we turn off the TRACE level, that means
   any complex logic we pass in to logTrace{ complex_stuff_here } simply won't get run, helping
   performance.
2. In the Logger.kt file, there is a public variable, logSettings, which can be adjusted at anytime
   from anywhere in the code to dynamically adjust which log types run.


Regular expressions (regex)
---------------------------

Typically, you should wrap strings to be used as regular expressions in triple-quotes: """foo"""
That way, you won't need to escape as much: "\\d" becomes """\d"""

Directories
-----------

The source code is organized using these standards for the directories (and thus, packages).
- _utility_ - these provide the main logic
- _exceptions_ - custom exceptions
- _types_ - custom types
- _api_ - HTTP endpoints
- _persistence_ - access to the database
