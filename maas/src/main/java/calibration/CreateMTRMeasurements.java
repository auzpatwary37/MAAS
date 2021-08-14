package calibration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.xml.sax.SAXException;

import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import maasPackagesV2.MaaSPackages;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class CreateMTRMeasurements {
	public static void main(String[] args) {
		String demandFileLoc = "MTRData/MTRodData.csv";
		String definitionFileLoc = "MTRData/stationCode.csv";
		
		Config c = ConfigUtils.createConfig();
		c.transit().setTransitScheduleFile("output_transitSchedule.xml.gz");
		Scenario scenario = ScenarioUtils.loadScenario(c);
		TransitSchedule ts = scenario.getTransitSchedule();
		String mode = "train";
		
		Map<String,FareCalculator>fareCalculators = new HashMap<>();
		SAXParser saxParser;
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("new Data/data/busFare.xml", busFareGetter);
			
		} catch (ParserConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			fareCalculators.put("train", new MTRFareCalculator("fare/mtr_lines_fares.csv",scenario.getTransitSchedule()));
			fareCalculators.put("bus", FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/busFareGTFS.json"));
			fareCalculators.put("minibus", busFareGetter.get());
			fareCalculators.put("LR", new LRFareCalculator("fare/light_rail_fares.csv"));
			fareCalculators.put("ferry",FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/ferryFareGTFS.json"));
			fareCalculators.put("tram", new UniformFareCalculator(2.6));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//Set<FareLink> fareLinks = new HashSet<>();
		Map<String,Double>fullFares = new HashMap<>();
		List<TransitStopFacility> stops = new ArrayList<>();
		for(TransitLine tl:ts.getTransitLines().values()) {
			for(TransitRoute tr:tl.getRoutes().values()) {
				if(tr.getTransportMode().equals(mode)) {
				tr.getStops().stream().forEach((stop)->{
					if(!stops.contains(stop.getStopFacility())) {
						stops.add(stop.getStopFacility());
						}
					});
				}
			}
		}
		Set<String> uniueStations = new HashSet<>();
		stops.forEach(s->{
			String ss = s.getId().toString().split("_")[1];
			ss = ss.replace("Up", "");
			ss = ss.replace("Down", "");
			uniueStations.add(ss);
		});
		for(int i=0;i<stops.size();i++) {
			for(int j=0;j<stops.size();j++) {
				if(i!=j) {
					FareLink fl = new FareLink(FareLink.NetworkWideFare,null,null,stops.get(i).getId(),stops.get(j).getId(),mode);
					double fullFare = fareCalculators.get(mode).getFares(null, null, fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					fullFares.put(fl.toString(), fullFare);					
				}
			}
		}
		
		Map<String,Tuple<Double,Double>>timeBean = new HashMap<>();
		Map<String,String> stationCode = new HashMap<>();
		timeBean.put("7", new Tuple<Double,Double>(7*3600.,8*3600.));
		timeBean.put("8", new Tuple<Double,Double>(8*3600.,9*3600.));
		timeBean.put("9", new Tuple<Double,Double>(9*3600.,10*3600.));
		
		Map<String,Tuple<Double,Double>> timeBeanAADT = new HashMap<>();
		timeBeanAADT.put("AADT", new Tuple<>(7*3600.,10*3600.));
		Measurements m = Measurements.createMeasurements(timeBean);
		Measurements mCom = Measurements.createMeasurements(timeBeanAADT);
		try {
			BufferedReader bf1 = new BufferedReader(new FileReader(new File(definitionFileLoc)));
			bf1.readLine();
			String line = null;
			while((line = bf1.readLine())!=null) {
				String[] part = line.split(",");
				stationCode.put(part[1], part[0]);
			}
			int total = 0;
			int notFound = 0;
			BufferedReader bf2 = new BufferedReader(new FileReader(new File(demandFileLoc)));
			bf2.readLine();
			 line = null;
				while((line = bf2.readLine())!=null) {
					total++;
					String[] part = line.split(",");
					String boardingStop = stationCode.get(part[0]);
					String alightinghStop = stationCode.get(part[1]);
					List<FareLink> fls = new ArrayList<>();
					
					String mIdString = "";
					String sep = "";
					for(String s:fullFares.keySet()) {
						if(s.contains(FareLink.NetworkWideFare) && s.contains(boardingStop) && s.contains(alightinghStop) && s.indexOf(boardingStop)< s.indexOf(alightinghStop)) {
							FareLink fl = new FareLink(s);
							fls.add(fl);
							mIdString = mIdString+sep+s;
							sep = ",";
						}
					}
					if(mIdString.length()!=0) {
						Id<Measurement> mId = Id.create(mIdString, Measurement.class);
						m.createAnadAddMeasurement(mIdString, MeasurementType.fareLinkVolumeCluster);
						
						m.getMeasurements().get(mId).putVolume("7", Double.parseDouble(part[2]));
						m.getMeasurements().get(mId).putVolume("8", Double.parseDouble(part[3]));
						m.getMeasurements().get(mId).putVolume("9", Double.parseDouble(part[4]));
						m.getMeasurements().get(mId).setAttribute(Measurement.FareLinkClusterAttributeName, fls);
						mCom.createAnadAddMeasurement(mIdString, MeasurementType.fareLinkVolumeCluster);
						mCom.getMeasurements().get(mId).setAttribute(Measurement.FareLinkAttributeName, fls);
						mCom.getMeasurements().get(mId).putVolume("AADT", Double.parseDouble(part[5]));
					}else {
						notFound++;
						//System.out.println("No Fare Links found for boarding "+ boardingStop + " and alaighting "+ alightinghStop + "And for volumes = "+ part[2] + " "+ part[3]+ " "+ part[5]);
					}
				}
				bf1.close();
				bf2.close();
				System.out.println("FareLinks not found for "+ notFound + " no of measurements out of "+ total +" measurements.");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		new MeasurementsWriter(m).write("MTRData/MTRMeasurements7_8_9.xml");
		new MeasurementsWriter(mCom).write("MTRData/MTRMeasurements7_9.xml");
		
	}
	

}
	