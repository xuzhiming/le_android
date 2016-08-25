#Release Note
##usage

- Drag MGLogAndroid-1.0.0.jar to your libs.

- Create MGAndroidLogger instance during Application Created like this.

	```
	
	try {
			String token = "57a873cbe4b015e62096e314/8a416e15da944f39a71c6ee696b93a3f";
			MGAndroidLogger.createInstance(this.getBaseContext(), true, token);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	```

	
-  Log your api request like this.
	
	```
		MGAndroidLogger logger = MGAndroidLogger.getInstance();
		Uri uri = Uri.parse(url);
		long cost = System.currentTimeMillis() - startRequestDate.getTime();
		logger.log("", uri.getHost(), uri.getPath(), startRequestDate, cost, 200);
		
		//or use the extensible method
		//
		//logger.log(jsonstring);
	
	```
