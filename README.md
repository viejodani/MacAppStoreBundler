# MacAppStoreBundler

Simple Java console application that signs and bundled Java applications. It uses the xmlwise library to edit plist files inside the bundled app.

to run it use the following sintax:

`java -jar MacAppStoreBundler.jar <path to bundled app> <package name> <signature> <app category> <entitlements file path> <destination package file path>`

<b>Path To Bundled App:</b> The full path where the Java bundled .app file is located.

<b>Package Name:</b> The package name that you registered in the Mac App Store.

<b>Signature:</b> Your name or company name that can sign the applications for the MacAppStore

<b>App Category</b>: The category the application will be. [See here to locate your category.](https://developer.apple.com/library/ios/documentation/General/Reference/InfoPlistKeyReference/Articles/LaunchServicesKeys.html)

<b>Entitlements File Path:</b> The path to the .entitlements file needed to sign the application with features like sandboxing.

<b>Destination Package File Path:</b> The location where the .pkg package will be created.

The xmlwise library copyright goes to original holders but the bundler is under MIT license
