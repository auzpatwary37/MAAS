package MaaSPackages;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import transitCalculatorsWithFare.FareLink;



public class MaaSPackagesReader extends DefaultHandler{
	
	private Map<String,MaaSPackage> packages=new HashMap<>();
	private String currentMaaSId = null;
	
	@Override 
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		
		if(qName.equalsIgnoreCase("MaaSPackage")) {
			String id=attributes.getValue("PackageId");
			double cost = Double.parseDouble(attributes.getValue("Cost"));
			double expTime = Double.parseDouble(attributes.getValue("ExpTime"));
			int maxTaxiTrip = Integer.parseInt(attributes.getValue("MaxTaxiTrip"));
			String operatorId = attributes.getValue("operatorId");
			
			this.packages.put(id, new MaaSPackage(id,operatorId, cost, maxTaxiTrip));
			this.currentMaaSId = id;
		}
		
		if(qName.equalsIgnoreCase(FareLink.FareLinkAttributeName)) {
			MaaSPackage m = this.packages.get(this.currentMaaSId);
			FareLink fl = new FareLink(attributes.getValue("Description"));
			double discount = Double.parseDouble(attributes.getValue("Discount"));
			double fullFare = Double.parseDouble(attributes.getValue("FullFare"));
			m.addFareLink(fl, discount, fullFare);
		}
		
		
		
	}
	
	@Override 
	public void endElement(String uri, String localName, String qName) {
		
	}
	
	public MaaSPackages readPackagesFile(String fileLoc) {
		
		try {
			SAXParserFactory.newInstance().newSAXParser().parse(fileLoc,this);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("IO Exception");
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		return new MaaSPackages(this.packages);
	}
}
