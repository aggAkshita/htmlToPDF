package org.example;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;

public class HTMLToPDF {
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter the path of the HTML file (use forward slashes): ");
        String htmlFilePath = scanner.nextLine();

        File htmlFile = new File(htmlFilePath);
        Document doc = Jsoup.parse(htmlFile, "UTF-8");

        HTMLToPDF mod = new HTMLToPDF();
        Object[] result = mod.modifyHTML(doc);

        Document modDoc = (Document) result[0];
        String itineraryNumber = (String) result[1];

        String pathWithoutFile = htmlFilePath.substring(0, htmlFilePath.lastIndexOf('/'));
        String pdfFilePath = pathWithoutFile + '/' + itineraryNumber + ".pdf";

        modDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        try (OutputStream os = new FileOutputStream(pdfFilePath)) {
            ITextRenderer renderer = new ITextRenderer();
            SharedContext cntxt = renderer.getSharedContext();
            cntxt.setPrint(true);
            cntxt.setInteractive(false);
            renderer.setDocumentFromString(modDoc.html());
            renderer.layout();
            renderer.createPDF(os);
            System.out.println("HTML converted to PDF successfully and saved at this path - " + pdfFilePath);
        }
    }

    public Object[] modifyHTML(Document htmlDoc) {
        Document htmlRemoveAgentRef = removeTableRow(htmlDoc, "Agent Reference:");
        Document htmlRemoveRefNo = removeTableRow(htmlRemoveAgentRef, "Reference Number");

        String itineraryNumber = getNextElementText(htmlRemoveRefNo, "Itinerary Number");

        Document htmlRemoveItineraryNo = removeTableRow(htmlRemoveRefNo, "Itinerary Number");
        Document htmlUpdateImage = updateImageSrc(htmlRemoveItineraryNo,
                "https://mcusercontent.com/b9b38543e81e56f3d1e9fc377/_thumbs/a605dd62-c37f-590a-dc11-bbbbf4ad29f1.png");
        Document htmlUpdatePassengerDetails = replaceTextInMatchingElements(htmlUpdateImage, "PASSENGER DETAILS",
                "GUEST & STAY DETAILS");
        Document htmlUpdatePassengerName = replaceTextInMatchingElements(htmlUpdatePassengerDetails, "Passenger Name",
                "Guest Name");
        Document htmlUpdatePassengerNationality = replaceTextInMatchingElements(htmlUpdatePassengerName,
                "Passenger Nationality", "Guest Nationality");
        Document htmlUpdateQuery = replaceTextInMatchingElements(htmlUpdatePassengerNationality, "Operations Team",
                "Contact us for any queries at,");
        Document htmlUpdateSupport = replaceTextInMatchingElements(htmlUpdateQuery, "dummyvendor FZ LLC",
                "Unravel Support");
        Document htmlUpdateEmail = replaceTextInMatchingElements(htmlUpdateSupport,
                "_support@dummyvendor.com", "support@gounravel.com");
        Document htmlUpdateServiceAndRequests = updateAdditionalServiceAndRequests(htmlUpdateEmail);
        Document htmlRemoveUnwantedUSDString = replaceTextInMatchingElements(htmlUpdateServiceAndRequests,
                                                                                     "(US Dollars)", "(Indian Rupees)");
        return new Object[]{convertCurrency(htmlRemoveUnwantedUSDString), itineraryNumber};
    }

    public Document removeTableRow(Document htmlDoc, String matchingText) {
        Elements tdElements = htmlDoc.select("td:containsOwn(" + matchingText + ")");
        for (Element tdElement : tdElements) {
            Element trElement = tdElement.closest("tr");
            if (trElement != null) {
                trElement.remove();
            }
        }
        return htmlDoc;
    }

    public Document updateImageSrc(Document htmlDoc, String newSrc) {
        Elements imgElements = htmlDoc.select("img");
        for (Element imgElement : imgElements) {
            imgElement.attr("src", newSrc);
        }

        return htmlDoc;
    }

    public Document replaceTextInMatchingElements(Document document, String oldText, String newText) {
        String htmlContent = document.outerHtml();
        String updatedHtmlContent = htmlContent.replace(oldText, newText);

        return Jsoup.parse(updatedHtmlContent);
    }

    public Document updateAdditionalServiceAndRequests(Document htmlDoc) {
        Elements elements = htmlDoc.select("td");

        boolean addServices = false;
        boolean addServicesNone = false;
        Element addServicesElement = null;
        Element addServicesNoneElement = null;

        boolean addRequests = false;
        boolean addRequestsNone = false;
        Element addRequestsElement = null;
        Element addRequestsNoneElement = null;

        boolean addServicesNoneRemoved = false;
        for (Element element : elements) {
            if (element.text().contains("none")) {
                if (addServices){
                    addServicesNone = true;
                    addServicesNoneElement = element;
                }
                if (addRequests){
                    addRequestsNone = true;
                    addRequestsNoneElement = element;
                }
            }

            if (addServices){
                addServices = false;
            }
            if (addRequests){
                addRequests = false;
            }

            if (element.text().contains("Additional Services")) {
                addServices = true;
                addServicesElement = element;
            }
            if (element.text().contains("Additional Requests")) {
                addRequests = true;
                addRequestsElement = element;
            }

            if (addServicesNone){
                addServicesNoneElement.remove();
                addServicesElement.remove();

                addServicesNoneRemoved = true;
                addServicesNone = false;
            }
            else if (addRequestsNone) {
                addRequestsNoneElement.remove();

                if (addServicesNoneRemoved){
                    addRequestsElement.text("No services/requests availed.");
                }
                else {
                    addRequestsElement.remove();
                }

                addRequestsNone = false;
            }
        }

        return htmlDoc;
    }

    public static Document convertCurrency(Document htmlDoc) {
        String regex = "(USD\\s*(\\d+\\.?\\d*))|((\\d+\\.?\\d*)\\s*USD)";
        Pattern pattern = Pattern.compile(regex);
        Elements elements = htmlDoc.getAllElements();
        double USD_TO_INR_RATE = 83.0;

        for (Element element : elements) {
            String elementHtml = element.html();
            Matcher matcher = pattern.matcher(elementHtml);

            StringBuffer newHtml = new StringBuffer();
            while (matcher.find()) {
                String replacement;
                if (matcher.group(2) != null) {
                    double usdAmount = Double.parseDouble(matcher.group(2));
                    double inrAmount = usdAmount * USD_TO_INR_RATE;
                    replacement = String.format("INR %.2f", inrAmount);
                } else {
                    double usdAmount = Double.parseDouble(matcher.group(4));
                    double inrAmount = usdAmount * USD_TO_INR_RATE;
                    replacement = String.format("INR %.2f", inrAmount);
                }
                matcher.appendReplacement(newHtml, replacement);
            }
            matcher.appendTail(newHtml);
            element.html(newHtml.toString());
        }
        return htmlDoc;
    }

    public static String getNextElementText(Document htmlDoc, String matchingString) {
        Element element = htmlDoc.getElementsContainingOwnText(matchingString).first();

        if (element != null) {
            Element nextElement = element.nextElementSibling();

            if (nextElement != null) {
                return nextElement.text();
            }
        }
        return "No matching element or next element found!";
    }
}




