package mycode.rumiantenna;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

public class App {

    static String revid = null;
    static int koepotaSize = -1;
    static String amebloLatestDate = null;
    static final Set<String> backlinks = new HashSet<>();

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                
                // wikipedia article
                from("timer:a?period=5m").choice().when((exchange) -> {
                    try {
                        Document get = Jsoup.connect("https://ja.wikipedia.org/w/api.php?action=parse&page=%E5%A4%A7%E4%B9%85%E4%BF%9D%E7%91%A0%E7%BE%8E&prop=revid&format=xml").get();
                        String attr = get.select("parse[title]").first().attr("revid");
                        if (revid == null) {
                            revid = attr;
                            System.out.println("wikipedia article ready: " + revid);
                            return false;
                        } else if (!revid.equals(attr)) {
                            revid = attr;
                            System.out.println("wikipedia article update: " + revid);
                            return true;
                        } else {
                            return false;
                        }
                    } catch (Throwable t) {
                        System.out.println("wikipedia article error");
                        return false;
                    }
                }).to("direct:notate");

                // wikipedia backlinks
                from("timer:b?period=5m").choice().when((exchange) -> {
                    try {
                        Document get = Jsoup.connect("https://ja.wikipedia.org/w/api.php?action=query&list=backlinks&bltitle=%E5%A4%A7%E4%B9%85%E4%BF%9D%E7%91%A0%E7%BE%8E&bllimit=500&format=xml&blnamespace=0").get();
                        Elements select = get.select("bl[title]");
                        if (backlinks.isEmpty()) {
                            select.stream().map((el) -> el.attr("title"))
                                    .forEach(backlinks::add);
                            System.out.println("wikipedia backlinks ready: " + backlinks.size());
                            return false;
                        } else {
                            List<String> collect = select.stream().map((el) -> el.attr("title"))
                                    .filter((title) -> backlinks.add(title))
                                    .collect(Collectors.toList());
                            if (collect.isEmpty()) {
                                return false;
                            } else {
                                collect.stream()
                                        .map((title) -> "wikipeia backlinks update: " + title)
                                        .forEach(System.out::println);
                                return true;
                            }
                        }
                    } catch (Throwable t) {
                        System.out.println("wikipedia backlinks error");
                        return false;
                    }
                }).to("direct:notate");

                // koepota
                from("timer:c?period=1h").choice().when((exchange) -> {
                    try {
                        Document get = Jsoup.connect("http://www.koepota.jp/eventschedule/").get();
                        Elements select = get.select("tr:contains(大久保瑠美)");
                        int size = select.size();
                        if (koepotaSize == -1) {
                            koepotaSize = size;
                            System.out.println("koepota ready: " + size);
                            return false;
                        } else if (koepotaSize < size) {
                            koepotaSize = size;
                            System.out.println("koepota update: " + size);
                            return true;
                        } else {
                            return false;
                        }
                    } catch (Throwable t) {
                        System.out.println("koepota error");
                        return false;
                    }
                }).to("direct:notate");

                // ameblo
                from("timer:d?period=1h").choice().when((exchange) -> {
                    try {
                        Document get = Jsoup.connect("http://ameblo.jp/rumiokubo/entrylist.html").get();
                        Element first = get.select(".newEntryTitle").first();
                        String updateTime = first.select(".updatetime").text();
                        String newEntryTitle = first.select(".newentrytitle").text();
                        if (amebloLatestDate == null) {
                            amebloLatestDate = updateTime;
                            System.out.println("ameblo ready: " + newEntryTitle + " " + amebloLatestDate);
                            return false;
                        } else if (!amebloLatestDate.equals(updateTime)) {
                            amebloLatestDate = updateTime;
                            System.out.println("ameblo update: " + newEntryTitle + " " + amebloLatestDate);
                            return true;
                        } else {
                            return false;
                        }
                    } catch (Throwable t) {
                        System.out.println("ameblo error");
                        return false;
                    }
                });

                // ただの個人用ヤフーメール
                from("imaps://username@imap.mail.yahoo.co.jp?disconnect=false&password=xxx&consumer.delay=5000")
                        .process((exchange) -> System.out.println("yahoo mail update"))
                        .to("direct:notate");

                from("direct:notate").process((exchange) -> {
                    CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier("COM3");
                    SerialPort serialPort = (SerialPort) portId.open("test", 5000);
                    serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8,
                            SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                    serialPort.getOutputStream().write(("a").getBytes());
                    serialPort.close();
                });
            }
        });
        main.run();
    }
}
