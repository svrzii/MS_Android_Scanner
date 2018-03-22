package com.example.matejsvrznjak.ms_scanner;

import android.util.Patterns;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BusinessCardData {
    public String name;
    public String surname;
    public String company;
    public String address;
    public ArrayList<String> addresses = new ArrayList<>();
    public ArrayList<String> emails = new ArrayList<>();
    public ArrayList<String> numbers = new ArrayList<>();
    public String telephone;
    public String mobile;
    public ArrayList<String> url = new ArrayList<>();
    public ArrayList<String> other = new ArrayList<>();

    public String processOCRResult(String ocrResult) {

        String resultOutput = "";

//        String OCRresult = ocrResult;

        ocrResult = ocrResult.replace("(0)", "").replace("(O)", "").replace("[0)", "").replace("(0]", "").replace("[0]", "").replace("[O]", "");
        ocrResult = ocrResult.replaceAll("[^+:.,()@/0123456789abcčćdđéefghijklmnopqrsštuvwxyzžABCČĆDĐEFGHIJKLMNOPQRSŠTUVWXYZŽ\\n]+", " ");

        ArrayList<String> stringList = new ArrayList<>(Arrays.asList(ocrResult.split("\\r?\\n"))); //new ArrayList is only needed if you absolutely need an ArrayList

        findPhoneNumbers(stringList);
        findAllOtherValues(stringList);

        resultOutput += "Name: " + this.name + "\n";
        resultOutput += "Surname: " + this.surname + "\n";
        resultOutput += "Mobile: " + this.mobile + "\n";
        resultOutput += "Telephone: " + this.telephone + "\n";
        resultOutput += "Company: " + this.company + "\n";

        for (String url : this.url) {
            resultOutput += "Url: ";
            resultOutput += url + "\n";
        }

        resultOutput += "Email: ";
        for (String email : this.emails) {
            resultOutput += email + "\n";
        }

        resultOutput += "Address: ";
        for (String address : this.addresses) {
            resultOutput += address + " ";
        }

        resultOutput += "\n\nNumbers:\n";
        for (String number : this.numbers) {
            resultOutput += number + "\n";
        }

        return resultOutput;
    }

    private void findPhoneNumbers(ArrayList<String> array) {
        for (String line : array) {
            line = line.trim();

            if (line.toLowerCase().contains("m:") || line.toLowerCase().contains("mob") || line.toLowerCase().contains("gsm")) {
                Matcher matcher = Patterns.PHONE.matcher(line);
                if (matcher.find()) {
                    if (matcher.group() != null && matcher.group().length() > 6) {
                        this.mobile =  matcher.group();
                    }
                } else {
                    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                    Iterable<PhoneNumberMatch> numbers = phoneUtil.findNumbers(line, "");

                    for (PhoneNumberMatch num : numbers) {
                        this.mobile = num.rawString();
                        break;
                    }
                }
                // allLines.remove(line);
            } else if (!line.toLowerCase().contains("fax") && (line.toLowerCase().contains("t:") || line.toLowerCase().contains("tel") || line.toLowerCase().contains("stac"))) {
                Matcher matcher = Patterns.PHONE.matcher(line);
                if (matcher.find()) {
                    if (matcher.group() != null && matcher.group().length() > 6) {
                        this.telephone =  matcher.group();
                    }
                } else {
                    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                    Iterable<PhoneNumberMatch> numbers = phoneUtil.findNumbers(line, "");

                    for (PhoneNumberMatch num : numbers) {
                        this.telephone = num.rawString();
                        break;
                    }
                }
//                allLines.remove(line);
            } else if (line.toLowerCase().contains("d.o.o.") || line.toLowerCase().contains("s.p.") || line.toLowerCase().contains("co.") || line.toLowerCase().contains("d.d.")) {
//               this.company = line;
//                allLines.remove(line);
            } else {
                Matcher emailMatcher = Patterns.EMAIL_ADDRESS.matcher(line); //compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
                Matcher nameMatcher = Pattern.compile("([A-ZĆČŠŽ]{1}[A-ZĆČŠŽa-zéćčšž]{3,}[ ]+[A-ZĆČŠŽ]{1}[A-ZĆČŠŽa-zéćčšž]{3,})[ ]*?$").matcher(line);
                Matcher webMatcher = Patterns.WEB_URL.matcher(line); //compile("(https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]\\.[^\\s]{2,})")
                Matcher addressMatcher = Pattern.compile("([a-zA-Z]{3,}+\\s)[0-9]{1,}").matcher(line); //([a-zA-Z]{3,}+\s)[0-9]+|([0-9]{1,}+\s([a-zA-Z]{2,}))

                PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                Iterable<PhoneNumberMatch> numbers = phoneUtil.findNumbers(line, "");

                if (!emailMatcher.find() && !webMatcher.find() && !nameMatcher.find() && !addressMatcher.find()) {
                    Matcher matcher = Patterns.PHONE.matcher(line);
                    if (matcher.find()) {
                        for (int i = 0; i  < matcher.groupCount(); i++) {
                            String num = matcher.group(i);
                            if (num != null && num.length() > 6) {
                                this.numbers.add(num);
                            }
                        }
                    } else {
                        for (PhoneNumberMatch num : numbers) {
                            this.numbers.add(num.rawString());
                            break;
                        }
                    }
                }
            }
        }
    }

    private void findAllOtherValues(ArrayList<String> array) {
        boolean nameDone = false;

        ArrayList<String> stringList2 = separate(array, "/");
//        ArrayList<String> stringList3 = separate(stringList2, ",");
        ArrayList<String> allLines = separate(stringList2, ";");

        for (String line : allLines) {
            line = line.trim();

            if (line.toLowerCase().contains("d.o.o.") || line.toLowerCase().contains("s.p.") || line.toLowerCase().contains("co.") || line.toLowerCase().contains("d.d.")) {
                this.company = line;
            } else {
                Matcher emailMatcher = Patterns.EMAIL_ADDRESS.matcher(line); //compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
                Matcher nameMatcher = Pattern.compile("([A-ZĆČŠŽ]{1}[A-ZĆČŠŽa-zéćčšž]{3,}[ ]+[A-ZĆČŠŽ]{1}[A-ZĆČŠŽa-zéćčšž]{3,})[ ]*?$").matcher(line);
                Matcher webMatcher = Patterns.WEB_URL.matcher(line); //compile("(https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]\\.[^\\s]{2,})")
                Matcher addressMatcher = Pattern.compile("([a-zA-Z]{3,}+\\s)[0-9]{1,}").matcher(line); //([a-zA-Z]{3,}+\s)[0-9]+|([0-9]{1,}+\s([a-zA-Z]{2,}))

                if (emailMatcher.find()) {
                    this.emails.add(emailMatcher.group());
                } else if (webMatcher.find()) {
                    this.url.add(webMatcher.group());
                } else if (nameMatcher.find()) {
                    if (!nameDone) {
                        String fullName = nameMatcher.group();

                        ArrayList<String> nameArray = new ArrayList<>(Arrays.asList(fullName.split(" ")));

                        if (nameArray.size() > 1) {
                            String name = nameArray.get(0);
                            if (name != null) {
                                this.name = name;
                            }

                            String surname = nameArray.get(1);
                            if (surname != null) {
                                this.surname = surname;
                            }

                            nameDone = true;
                        }
                    } else if (addressMatcher.find()) {
                        this.addresses.add(line);
                    } else {
                        this.other.add(line);
                    }
                } else if (addressMatcher.find()) {
                    this.addresses.add(line);
                }
            }
        }
    }

    private ArrayList<String> separate(ArrayList<String> currentArray, String separator) {
        ArrayList<String> stringList = new ArrayList<>();

        for (String item : currentArray) {
            String[] splitItems = item.split(separator);
            if (splitItems.length > 1) {
                for (String newItem : splitItems) {
                    stringList.add(newItem);
                }
            } else {
                stringList.add(item);
            }
        }

        return  stringList;
    }
}
