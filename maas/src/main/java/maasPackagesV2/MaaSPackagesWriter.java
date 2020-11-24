package maasPackagesV2;

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

import transitCalculatorsWithFare.FareLink;




public class MaaSPackagesWriter extends DefaultHandler{
	private final MaaSPackages maas;
	
	public MaaSPackagesWriter(MaaSPackages maas) {
		this.maas=maas;
	}
	
	public void write(String fileLoc) {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

			Document document = documentBuilder.newDocument();

			Element rootEle = document.createElement("MaaSPackages");
			
			for(MaaSPackage mm:this.maas.getMassPackages().values()) {
				Element maas=document.createElement("MaaSPackage");
				maas.setAttribute("PackageId", mm.getId());
				maas.setAttribute("Cost", Double.toString(mm.getPackageCost()));
				maas.setAttribute("ExpTime", Double.toString(mm.getPackageExpairyTime()));
				maas.setAttribute("MaxTaxiTrip", Integer.toString(mm.getMaxTaxiTrip()));
				maas.setAttribute("operatorId", mm.getOperatorId());
				maas.setAttribute("ReimbursementRatio", mm.convertOperatorReimbursementRatioToString());
				mm.getFareLinks().values().forEach((fl)->{
					Element fareLink = document.createElement(FareLink.FareLinkAttributeName);
					fareLink.setAttribute("Description", fl.toString());
					fareLink.setAttribute("Discount", Double.toString(mm.getDiscounts().get(fl.toString())));
					fareLink.setAttribute("FullFare", Double.toString(mm.getFullFare().get(fl.toString())));
					fareLink.setAttribute("operatorId", mm.getOperatorId(fl));
					maas.appendChild(fareLink);
				});
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
