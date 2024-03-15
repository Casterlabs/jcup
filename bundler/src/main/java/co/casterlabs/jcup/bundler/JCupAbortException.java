package co.casterlabs.jcup.bundler;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class JCupAbortException extends Exception {
    private static final long serialVersionUID = 4144009082451762426L;

    public final int desiredExitCode;

}
