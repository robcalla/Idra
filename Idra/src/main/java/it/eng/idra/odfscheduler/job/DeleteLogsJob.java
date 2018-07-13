/*******************************************************************************
 * Idra - Open Data Federation Platform
 *  Copyright (C) 2018 Engineering Ingegneria Informatica S.p.A.
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package it.eng.idra.odfscheduler.job;

import java.time.ZonedDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;

import it.eng.idra.management.PersistenceManager;

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class DeleteLogsJob implements Job{

	private static Logger logger = LogManager.getLogger(DeleteLogsJob.class);
	
	public DeleteLogsJob() {}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		// TODO Auto-generated method stub
		logger.info("Start Delete Logs job");
		PersistenceManager jpa = new PersistenceManager();
		try {
			ZonedDateTime now = ZonedDateTime.now();
			jpa.deleteLogs(now);
			logger.info("Logs deleted ");
		} catch (Exception e) {
			logger.error("Error during logs deletion "+e.getLocalizedMessage());
			e.printStackTrace();
		}finally {
			jpa.jpaClose();
		}
	
	}

}
