# flex-clip
Android clipboard listener service implementation. Get all addresses within the Amazon Flex app and save to JSON.
# How it works:
Using a service that runs in the background we listen for changes to the clipboard. When the clip changes, write the text to a JSON file stored on the device. The format of the JSON is as follows:

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

Each object within the JSON Array "stops" is given a unique "objectId" number. The "stopNumber" is based on the order in which the object is placed. Finally "address" is the actual text that is withing the clipboard.

APK available here: https://github.com/Jwillc/flex-clip/blob/master/flexClip.apk
