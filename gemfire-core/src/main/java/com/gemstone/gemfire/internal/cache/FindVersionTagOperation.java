/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
/**
 * 
 */
package com.gemstone.gemfire.internal.cache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.gemstone.gemfire.CancelException;
import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.distributed.internal.DM;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.DistributionMessage;
import com.gemstone.gemfire.distributed.internal.HighPriorityDistributionMessage;
import com.gemstone.gemfire.distributed.internal.MessageWithReply;
import com.gemstone.gemfire.distributed.internal.ReplyMessage;
import com.gemstone.gemfire.distributed.internal.ReplyProcessor21;
import com.gemstone.gemfire.internal.InternalDataSerializer;
import com.gemstone.gemfire.internal.cache.versions.VersionTag;
import com.gemstone.gemfire.internal.logging.LogService;

/**
 * @author bruce
 *
 */
public class FindVersionTagOperation {
  private static final Logger logger = LogService.getLogger();
  
  public static VersionTag findVersionTag(LocalRegion r, EventID eventId, boolean isBulkOp) {
    DM dm = r.getDistributionManager();
    Set recipients;
    if (r instanceof DistributedRegion) {
      recipients = ((DistributedRegion)r).getDistributionAdvisor().adviseCacheOp();
    } else {
      recipients = ((PartitionedRegion)r).getRegionAdvisor().adviseDataStore();
    }
    ResultReplyProcessor processor = new ResultReplyProcessor(dm, recipients);
    FindVersionTagMessage msg = new FindVersionTagMessage(recipients, processor.getProcessorId(), r.getFullPath(), eventId, isBulkOp);
    dm.putOutgoing(msg);
    try {
      processor.waitForReplies();
    } catch (InterruptedException e) {
      dm.getCancelCriterion().checkCancelInProgress(e);
      Thread.currentThread().interrupt();
      return null;
    }
    return processor.getVersionTag();
  }
  
  public static class ResultReplyProcessor extends ReplyProcessor21 {

    VersionTag versionTag;
    
    public ResultReplyProcessor(DM dm, Collection initMembers) {
      super(dm, initMembers);
    }
    
    @Override
    public void process(DistributionMessage msg) {
      if (msg instanceof VersionTagReply) {
        VersionTagReply reply = (VersionTagReply) msg;
        if (reply.versionTag != null) {
          this.versionTag = reply.versionTag;
          this.versionTag.replaceNullIDs(reply.getSender());
        }
      }
      super.process(msg);
    }

    public VersionTag getVersionTag() {
      return versionTag;
    }
    
    @Override
    public boolean stillWaiting() {
      return this.versionTag == null && super.stillWaiting();
    }

  }

  /**
   * FindVersionTagOperation searches other members for version information for a replayed
   * operation.  If we don't have version information the op may be applied by
   * this cache as a new event.  When the event is then propagated to other servers
   * that have already seen the event it will be ignored, causing an inconsistency.
   * @author bruce
   */
  public static class FindVersionTagMessage extends HighPriorityDistributionMessage 
     implements MessageWithReply {
    
    int processorId;
    String regionName;
    EventID eventId;
    private boolean isBulkOp;
    
    protected FindVersionTagMessage(Collection recipients, int processorId, String regionName, EventID eventId, boolean isBulkOp) {
      super();
      setRecipients(recipients);
      this.processorId = processorId;
      this.regionName = regionName;
      this.eventId = eventId;
      this.isBulkOp = isBulkOp;
    }

    /** for deserialization */
    public FindVersionTagMessage() {
    }

    /* (non-Javadoc)
     * @see com.gemstone.gemfire.distributed.internal.DistributionMessage#process(com.gemstone.gemfire.distributed.internal.DistributionManager)
     */
    @Override
    protected void process(DistributionManager dm) {
      VersionTag result = null;
      try {
        LocalRegion r = findRegion();
        if (r == null) {
          if (logger.isDebugEnabled()) {
            logger.debug("Region not found, so ignoring version tag request: {}", this);
          }
          return;
        }
        if(isBulkOp) {
          result = r.findVersionTagForClientBulkOp(eventId);
          
        } else {
          result = r.findVersionTagForClientEvent(eventId);
        }
        if (result != null) {
          result.replaceNullIDs(r.getVersionMember());
        }
        if (logger.isDebugEnabled()) {
          logger.debug("Found version tag {}", result);
        }
 
      }
      catch (RuntimeException e) {
        logger.warn("Exception thrown while searching for a version tag", e);
      }
      finally {
        VersionTagReply reply = new VersionTagReply(result);
        reply.setProcessorId(this.processorId);
        reply.setRecipient(getSender());
        try {
          dm.putOutgoing(reply);
        } catch (CancelException e) {
          // can't send a reply, so ignore the exception
        }
      }
    }

    private LocalRegion findRegion() {
      GemFireCacheImpl cache = null;
      try {
        cache = GemFireCacheImpl.getInstance();
        if (cache != null) {
          return cache.getRegionByPathForProcessing(regionName);
        }
      } catch (CancelException e) {
        // nothing to do
      }
      return null;
    }

    public int getDSFID() {
      return FIND_VERSION_TAG;
    }
    
    @Override
    public void toData(DataOutput out) throws IOException {
      super.toData(out);
      out.writeInt(this.processorId);
      out.writeUTF(this.regionName);
      InternalDataSerializer.invokeToData(this.eventId, out);
      out.writeBoolean(this.isBulkOp);
    }

    @Override
    public void fromData(DataInput in) throws IOException, ClassNotFoundException {
      super.fromData(in);
      this.processorId = in.readInt();
      this.regionName = in.readUTF();
      this.eventId = new EventID();
      InternalDataSerializer.invokeFromData(this.eventId, in);
      this.isBulkOp = in.readBoolean();
    }
    
    @Override
    public String toString() {
      return this.getShortClassName() + "(processorId=" + this.processorId
      + ";region=" + this.regionName
      + ";eventId=" + this.eventId
      + ";isBulkOp=" + this.isBulkOp
      + ")";
    }
  }

  public static class VersionTagReply extends ReplyMessage {
    VersionTag versionTag;
    
    VersionTagReply(VersionTag result) {
      this.versionTag = result;
    }

    /** for deserialization */
    public VersionTagReply() {
    }
    
    @Override
    public String toString() {
      return "VersionTagReply("+this.versionTag+")";
    }

    @Override
    public void toData(DataOutput out) throws IOException {
      super.toData(out);
      DataSerializer.writeObject(this.versionTag, out);
    }

    @Override
    public void fromData(DataInput in) throws IOException,
        ClassNotFoundException {
      super.fromData(in);
      this.versionTag = (VersionTag)DataSerializer.readObject(in);
    }

    @Override
    public int getDSFID() {
      return VERSION_TAG_REPLY;
    }
  }

}
