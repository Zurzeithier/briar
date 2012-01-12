package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.ByteArrayInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionDecrypterImplTest extends BriarTestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher frameCipher;
	private final ErasableKey frameKey;

	public ConnectionDecrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		frameCipher = crypto.getFrameCipher();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testInitiatorDecryption() throws Exception {
		testDecryption(true);
	}

	@Test
	public void testResponderDecryption() throws Exception {
		testDecryption(false);
	}

	private void testDecryption(boolean initiator) throws Exception {
		// Calculate the ciphertext for the first frame
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, frameCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = new byte[plaintext.length];
		frameCipher.doFinal(plaintext, 0, plaintext.length, ciphertext);
		// Calculate the ciphertext for the second frame
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = new byte[plaintext1.length];
		frameCipher.doFinal(plaintext1, 0, plaintext1.length, ciphertext1);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use a ConnectionDecrypter to decrypt the ciphertext
		ConnectionDecrypter d = new ConnectionDecrypterImpl(in, frameCipher,
				frameKey, MAC_LENGTH);
		// First frame
		byte[] decrypted = new byte[MAX_FRAME_LENGTH];
		assertEquals(plaintext.length, d.readFrame(decrypted));
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertEquals(plaintext1.length, d.readFrame(decrypted));
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}
}
