package es.company.etc.subsystems.gmail;

import org.alfresco.error.AlfrescoRuntimeException;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import es.company.etc.repo.gmail.EmailSiteClassifier;

public class GetGmailScheduled implements Job {
	

	public GetGmailScheduled(){
		
	}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		// TODO Auto-generated method stub
		
		JobDataMap jobData = context.getJobDetail().getJobDataMap();
        // extract the feed cleaner to use
        Object aliasEmailSite = jobData.get("aliasEmailSite");
        
        
        if (aliasEmailSite == null || !(aliasEmailSite instanceof EmailSiteClassifier))
        {
            throw new AlfrescoRuntimeException(
                    "aliasEmailSiteObj data must contain valid 'EmailSiteClassifier' reference");
        }
        
        EmailSiteClassifier aliasEmailSiteServ = (EmailSiteClassifier)aliasEmailSite;
        aliasEmailSiteServ.execute();
	}

	
}


