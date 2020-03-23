package singlePlanAlgo;

import java.io.FileOutputStream;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;



public class MAASPackagesWriter extends DefaultHandler{
	private final MAASPackages maas;
	
	public MAASPackagesWriter(MAASPackages maas) {
		this.maas=maas;
	}
	
	public void write(String fileLoc) {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

			Document document = documentBuilder.newDocument();

			Element rootEle = document.createElement("MAASPackages");
			
			for(MAASPackage mm:this.maas.getMassPackages().values()) {
				Element maas=document.createElement("MAASPackage");
				maas.setAttribute("PackageId", mm.getId());
				maas.setAttribute("Cost", Double.toString(mm.getPackageCost()));
				maas.setAttribute("ExpTime", Double.toString(mm.getPackageExpairyTime()));
				maas.setAttribute("MaxTaxiTrip", Integer.toString(mm.getMaxTaxiTrip()));
				maas.setAttribute("operatorId", mm.getOperatorId());
				if(!mm.getDiscounts().isEmpty()) {
					for(Entry<Id<TransitLine>, Double> d:mm.getDiscounts().entrySet()) {
						Element discount=document.createElement("Discount");
						discount.setAttribute("TransitLine", d.getKey().toString());
						discount.setAttribute("DiscountValue", Double.toString(d.getValue()));
						discount.setAttribute("PackageId", mm.getId());
						maas.appendChild(discount);
					}
				}
				String tlId="";
				String seperator= "";
				for(Id<TransitLine> tl:mm.getTransitLines()) {
					tlId=tlId+tl.toString()+seperator;
					seperator=",";
				}
				maas.setAttribute("TransitLines", tlId);

				
				rootEle.appendChild(maas);
			}
			document.appendChild(rootEle);
			

			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			//tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "measurements.dtd");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			tr.transform(new DOMSource(document), new StreamResult(new FileOutputStream(fileLoc)));


		}catch(Exception e) {
			System.out.println(e.getMessage());
		}

	}
	
}
