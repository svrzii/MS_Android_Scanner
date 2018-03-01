# MS_Android_Scanner
Creating a business card scanner (work in progress)

Libraries that I use:
Tess two (uses TesseractOCR): https://github.com/rmtheis/tess-two
Document Scanner: https://github.com/Aniruddha-Tapas/Document-Scanner
Image cropper: https://github.com/ArthurHub/Android-Image-Cropper
OpenCV 3.4.0: https://opencv.org/releases.html

After OCR I check the name, phone numbers, email address, web address, home address from the recognized text. (For now we check for Slovenian matches)
