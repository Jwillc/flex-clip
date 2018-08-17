# flex-clip
Android clipboard listener service implementation. Get all addresses within the Amazon Flex app and save to JSON.
# How it works:
Using a service that runs in the background we listen for changes to the clipboard. When the clip changes, write the text to a JSON file stored on the device. The format of the JSON is as follows:
```
{
	"stops": [
		{
			"objectId":"001",
			"stopNumber":"Stop Number",
			"address":"Address Here"
		},
		{
			"objectId":"002",
			"stopNumber":"Second stop Number",
			"address":"address Here"
		}
	]
}
```
Each object within the JSON Array "stops" is given a unique "objectId" number. The "stopNumber" is based on the order in which the object is placed. Finally "address" is the actual text that is within the clipboard.

APK available here: https://github.com/Jwillc/flex-clip/blob/master/flexClip.apk

# Usage Example:
Lets say you want to get all the addresses for your route from the Amazon Flex app for use in an external navigation provider. Normally you would have to get each address one at a time. Switch from the Amazon Flex app to your external app and back again. With this service you can remain on the Amazon Flex app, copy each address by long pressing them, and they'll automatically be saved, in order, to a JSON file.
