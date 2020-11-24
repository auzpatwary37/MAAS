package singlePlanAlgo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

import maasPackagesV2.MaaSPackages;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

public class ModeSpecificTripCounterAndRevenueHandler implements PersonMoneyEventHandler{
	
	private Map<String,Double>tripCount = new HashMap<>();
	private Map<String,Double> revenueCount = new HashMap<>();
	private Map<String,Double> packCount = new HashMap<>();
	private Map<String,Double> packageTripCount = new HashMap<>();
	
	@Override
	public void handleEvent(PersonMoneyEvent event) {
		if(event.getPurpose().equals(FareLink.FareTransactionName)) {//farePayment event
			FareLink fl = new FareLink(event.getTransactionPartner());
			tripCount.compute(fl.getMode(),(k,v)->v==null?1:v+1);
			revenueCount.compute(fl.getMode(), (k,v)->v==null?event.getAmount():v+event.getAmount());
		}else if(event.getPurpose().equals(MaaSUtil.MaaSDiscountReimbursementTransactionName)){
			FareLink fl = new FareLink(event.getTransactionPartner());
			revenueCount.compute(fl.getMode(), (k,v)->v==null?-1*event.getAmount():v-event.getAmount());
			packageTripCount.compute(fl.getMode(), (k,v)->v==null?1:v+1);
		}else if(event.getPurpose().equals(MaaSUtil.MaaSOperatorpacakgeRevenueTransactionName)) {
			String packId = event.getTransactionPartner().split("__")[0];
			revenueCount.compute(packId, (k,v)->v==null?event.getAmount():v+event.getAmount());
			packCount.compute(packId, (k,v)->v==null?1:v+1);
		}
	}

	public Map<String, Double> getTripCount() {
		return tripCount;
	}

	public Map<String, Double> getRevenueCount() {
		return revenueCount;
	}

	public Map<String, Double> getPackCount() {
		return packCount;
	}

	public Map<String, Double> getPackageTripCount() {
		return packageTripCount;
	}
	
	public void writeCsv(String fileLoc) {
		try {
			FileWriter fw = new FileWriter(new File(fileLoc),true);
			//header
			Set<String> modes = new HashSet<>();
			modes.addAll(this.tripCount.keySet());
			modes.addAll(this.packageTripCount.keySet());
			modes.addAll(this.revenueCount.keySet());
			modes.addAll(this.packCount.keySet());
			
			List<String> modeList = new ArrayList<>(modes);
			fw.append("Counter");
			for(String m:modeList){
				fw.append(","+m);
			}
			fw.append("\n");
			
			fw.append("tripCounter");
			for(String m:modeList){
				fw.append(","+this.tripCount.get(m));
			}
			fw.append("\n");
			

			fw.append("revenueCounter");
			for(String m:modeList){
				fw.append(","+this.revenueCount.get(m));
			}
			fw.append("\n");
			
			fw.append("packageTripCounter");
			for(String m:modeList){
				fw.append(","+this.packageTripCount.get(m));
			}
			fw.append("\n");
			
			fw.append("pacakgeCounter");
			for(String m:modeList){
				fw.append(","+this.packCount.get(m));
			}
			fw.append("\n");
			fw.flush();
			fw.close();
						
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		String eventFileLoc = "toyScenarioLarge/consolResult/output_events_oneStep_1.xml.gz";
		String fileLoc = "toyScenarioLarge/consolResult/output_events_oneStep_1.csv";
		EventsManager events = EventsUtils.createEventsManager();
		ModeSpecificTripCounterAndRevenueHandler dataCollector = new ModeSpecificTripCounterAndRevenueHandler();
		events.addHandler(dataCollector);
		new MatsimEventsReader(events).readFile(eventFileLoc);
		dataCollector.writeCsv(fileLoc);
		
	}

}
