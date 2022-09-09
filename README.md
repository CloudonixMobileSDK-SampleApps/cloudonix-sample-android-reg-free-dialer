# Cloudonix Mobile SDK - Registration-Free Sample Application 

This repository contains an Android project to build an application that uses the [Cloudonix Registration-Free protocol](
https://docs.cloudonix.io/#/platform/registration-free/) and push notifications to make and receive VoIP calls.

To successfully run this application, it needs a supporting Registration-Free server, to be configured in the application's
source code. An fully working sample server for NodeJS can be found in the [Cloudonix Mobile SDK Sample Apps page on Github](
https://github.com/CloudonixMobileSDK-SampleApps/cloudonix-sample-reg-free-server)

## Required External Setup

For this sample application to work, you need to have the following things already set up and configured:

1. A Cloudonix Platform account - go to the [Cloudonix Cockpit](https://cockpit.cloudonix.io) to log in or create a new
   one.
2. Set up a [Registration-Free Control server](https://github.com/CloudonixMobileSDK-SampleApps/cloudonix-sample-reg-free-server)
   that is a publicly accessible (i.e. can be accessed by the Cloudonix Platform and whatever network you use for this 
   test app). You can use the sample server that is provided by Cloudonix (linked above).
3. Setup your Cloudonix account with the following things:
   1. A Cloudonix domain - one should be created for you automatically when you create the Cloudonix Platform account -
      make sure it fits your needs or create a new one.
   2. Set your Registration-Free Control Endpoint for incoming messages in your Cloudonix domain's settings.
   3. Create a few subscribers in your Cloudonix domain - at least one for sending a call and another for receiving it.
      it is recommended to leave the "SIP password" field empty for the subscribers you want to run the Registration-Free
      application on.
4. Set up Firebase Cloud Messaging for your app (detailed below).

### Firebase Cloud Messaging Setup

The [Firebase setup help page](https://firebase.google.com/docs/android/setup) details everything that needs to be done,
and you should be familiar with it when developing applications that accept push notifications. This project already
has all the generic setup and you just need to associate it with your Firebase project, by following these steps:

1. Log in to the [Firebase Console](https://console.firebase.google.com/)
2. Select the project you have already set up for your Registration-Free Control server.
3. Click the "Android" icon (or "Add app")
4. Fill in the details of the "Add app" form:
   - "Android package name": The application ID of the sample app. This would be `io.cloudonix.samples.regfreedialer`
     if you have not changed it.
   - Any optional fields you wish
5. Click "Register", and then download the `google-services.json` file that will be offered.
6. Put the `google-services.json` file in the `app` folder.

## Build Instructions

1. Copy the `CloudonixSDK.aar` file from the Cloudonix Mobile SDK download package to the `app/libs` directory.
2. Copy your license key file to `app/src/main/res/raw/cloudonix_license_key` .
3. Edit the file `strings.xml` to set the following fields:
    - `regfree_register` - the URL to the Registration-Free Control Server device registration endpoint. If you are using
      the [Cloudonix sample server](https://github.com/CloudonixMobileSDK-SampleApps/cloudonix-sample-reg-free-server),
      this would be the ngrok URL followed by `/devices`.
    - `cloudonix_domain` - the name of the Cloudonix domain you have set up in the Cloudonix Cockpit
    - `msisdn` - the MSISDN for the subscriber account on your Cloudonix domain.
4. Build the application using Android Studio and run it.
