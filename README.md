# MS_Android_Scanner
Creating a business card scanner (work in progress)

Libraries that I use:
Document Scanner: https://github.com/Aniruddha-Tapas/Document-Scanner
Image cropper: https://github.com/ArthurHub/Android-Image-Cropper
Libphonenumber: https://github.com/googlei18n/libphonenumber
Google Vision: https://github.com/dandar3/android-google-play-services-vision
Google Cloud Vision: https://github.com/ArthurHub/Android-Image-Cropper (I commented all the code for this lib & deleted my API key, I used this one in my primary project for better results)

Libraries that I used:
Tess two (uses TesseractOCR): https://github.com/rmtheis/tess-two (deleted)
OpenCV 3.4.0: https://opencv.org/releases.html (deleted)

After OCR I check the name, phone numbers, email address, web address, home address from the recognized text. (For now we check for Slovenian matches)
