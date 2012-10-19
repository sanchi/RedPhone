package org.thoughtcrime.redphone;

/**
 * Indicates than an exception has occurred in the client.
 * Typically, causes a message to display and terminates the current call.
 *
 * @author Stuart O. Anderson
 */
public class ClientException extends Exception {
  private final int msgId;

  public ClientException(int msgId) {
    this.msgId = msgId;
  }

  public int getMsgId() {
    return msgId;
  }
}
