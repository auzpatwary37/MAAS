package singlePlanAlgo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;



public class MAASPackagesReader extends DefaultHandler{
	
	private Map<String,MAASPackage> packages=new HashMap<>();
	
	@Override 
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		
		if(qName.equalsIgnoreCase("MAASPackage")) {
			String id=attributes.getValue("PackageId");
			double cost = Double.parseDouble(attributes.getValue("Cost"));
			double expTime = Double.parseDouble(attributes.getValue("ExpTime"));
			int maxTaxiTrip = Integer.parseInt(attributes.getValue("MaxTaxiTrip"));
			String operatorId = attributes.getValue("operatorId");
			
			String lineIds =attributes.getValue("TransitLines");
			
			List<Id<TransitLine>> lines= new ArrayList<>();
			
			for(String s:lineIds.split(",")) {
				lines.add(Id.create(s, TransitLine.class));
			}
			
			this.packages.put(id, new MAASPackage(id,operatorId, lines, maxTaxiTrip, cost, expTime));
			
			
		}
		
		if(qName.equalsIgnoreCase("Discount")) {
			Id<TransitLine> lineId = Id.create(attributes.getValue("TransitLine"),TransitLine.class);
			String packageId = attributes.getValue("PackageId");
			Double discount = Double.parseDouble(attributes.getValue("DiscountValue"));
			this.packages.get(packageId).getDiscounts().put(lineId, discount);
		}
		
		
		
	}
	
	@Override 
	public void endElement(String uri, String localName, String qName) {
		
	}
	
	public MAASPackages readPackagesFile(String fileLoc) {
		
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
		return new MAASPackages(this.packages);
	}
}
