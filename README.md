## appcenter_upload_apk
This project demonstrate how to use app-center api in order to convert your apk in an easily shareable link. This was developed with the idea of being used as a kts script, it has no external dependencies and cab be converted in a gradle task in less than 60s

(this project use the non deprecated app_center_api)

## About App Center
App Center Distribute is a tool for developers to quickly release builds to end user devices. Distribute supports Android, iOS, macOS, UWP, WPF and WinForms apps, allowing you to manage app distribution across multiple platforms all in one place.

## Usage

5 parameter are required
* apiToken -> The AppCenter API Token to use"
* ownerName -> The app's Owner (organisation) name
* appName -> The app's name
* file -> path to the apk file to upload
* note -> path to a file containing the release notes
* destination -> he group to distribute to

Check the official in order to setup your environement then run run.sh script

##  Issues and Feedback
For any issues or feedback about this project, please open a GitHub issue.
