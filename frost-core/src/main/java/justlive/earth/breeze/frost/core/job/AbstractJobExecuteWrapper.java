package justlive.earth.breeze.frost.core.job;

import java.time.ZonedDateTime;
import java.util.Date;
import org.springframework.util.StringUtils;
import justlive.earth.breeze.frost.api.model.JobExecuteRecord;
import justlive.earth.breeze.frost.api.model.JobInfo;
import justlive.earth.breeze.frost.core.config.JobProperties;
import justlive.earth.breeze.frost.core.notify.Event;
import justlive.earth.breeze.frost.core.notify.EventPublisher;
import justlive.earth.breeze.frost.core.persistence.JobRepository;
import justlive.earth.breeze.frost.core.util.IpUtils;
import justlive.earth.breeze.frost.core.util.SpringBeansHolder;
import justlive.earth.breeze.snow.common.base.exception.CodedException;

/**
 * 执行job抽象
 * 
 * @author wubo
 *
 */
public abstract class AbstractJobExecuteWrapper extends AbstractWrapper {

  protected JobInfo jobInfo;

  protected JobExecuteRecord jobRecord;

  protected void before() {
    JobRepository jobRepository = SpringBeansHolder.getBean(JobRepository.class);
    JobLogger jobLogger = SpringBeansHolder.getBean(JobLogger.class);
    jobRecord = jobRepository.findJobExecuteRecordById(jobLogger.findLoggerId(jobInfo.getId()));
    if (jobRecord == null) {
      jobRecord = this.record(jobInfo.getId(), jobLogger.bindLog(jobInfo.getId()));
    }
    jobRecord.setExecuteTime(Date.from(ZonedDateTime.now().toInstant()));
  }

  @Override
  public void success() {
    JobRepository jobRepository = SpringBeansHolder.getBean(JobRepository.class);
    jobRecord.setExecuteStatus(JobExecuteRecord.STATUS.SUCCESS.name());
    jobRecord.setExecuteMsg(String.format("执行成功 [%s]", address()));
    jobRepository.updateJobRecord(jobRecord);
  }

  @Override
  public void exception(Exception e) {
    super.exception(e);

    JobRepository jobRepository = SpringBeansHolder.getBean(JobRepository.class);

    jobRecord.setExecuteStatus(JobExecuteRecord.STATUS.FAIL.name());
    String cause;
    if (e instanceof CodedException) {
      cause = ((CodedException) e).getErrorCode().toString();
    } else {
      cause = e.getMessage();
    }
    jobRecord.setExecuteMsg(String.format("执行失败 [%s] [%s]", address(), cause));

    jobRepository.updateJobRecord(jobRecord);

    IJob job = getIJob();
    if (job.exception()) {
      EventPublisher publisher = SpringBeansHolder.getBean(EventPublisher.class);
      publisher.publish(new Event(jobInfo, Event.TYPE.EXECUTE_FAIL.name(),
          jobRecord.getExecuteMsg(), jobRecord.getExecuteTime().getTime()));
    }

  }

  protected abstract IJob getIJob();

  private String address() {
    JobProperties props = SpringBeansHolder.getBean(JobProperties.class);
    String address = props.getExecutor().getIp();
    if (!StringUtils.hasText(address)) {
      address = IpUtils.ip();
    }
    address += IpUtils.SEPERATOR + props.getExecutor().getPort();
    return address;
  }
}
