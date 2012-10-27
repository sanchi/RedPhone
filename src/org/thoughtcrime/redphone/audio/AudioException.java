package org.thoughtcrime.redphone.audio;

/**
 * An exception related to the audio subsystem.
 *
 * @author Stuart O. Anderson
 */
public class AudioException extends Exception{
  private final int clientMessage;

  public AudioException(int clientMessage) {
    this.clientMessage = clientMessage;
  }

  public AudioException(AudioException cause) {
    super(cause);
    this.clientMessage = cause.clientMessage;
  }

  public int getClientMessage() {
    return clientMessage;
  }
}
