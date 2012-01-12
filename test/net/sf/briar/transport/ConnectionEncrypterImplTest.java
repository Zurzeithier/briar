package net.sf.briar.transport;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionEncrypterImplTest extends BriarTestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;

	public ConnectionEncrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		tagKey = crypto.generateTestKey();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testInitiatorEncryption() throws Exception {
		testEncryption(true);
	}

	@Test
	public void testResponderEncryption() throws Exception {
		testEncryption(false);
	}

	private void testEncryption(boolean initiator) throws Exception {
		// Calculate the expected tag
		byte[] tag = TagEncoder.encodeTag(0, tagCipher, tagKey);
		// Calculate the expected ciphertext for the first frame
		byte[] iv = new byte[frameCipher.getBlockSize()];
		byte[] plaintext = new byte[123 + MAC_LENGTH];
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext);
		// Calculate the expected ciphertext for the second frame
		byte[] plaintext1 = new byte[1234 + MAC_LENGTH];
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = frameCipher.doFinal(plaintext1);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(tag);
		out.write(ciphertext);
		out.write(ciphertext1);
		byte[] expected = out.toByteArray();
		// Use a ConnectionEncrypter to encrypt the plaintext
		out.reset();
		ConnectionEncrypter e = new ConnectionEncrypterImpl(out, Long.MAX_VALUE,
				tagCipher, frameCipher, tagKey, frameKey);
		e.writeFrame(plaintext, plaintext.length);
		e.writeFrame(plaintext1, plaintext1.length);
		byte[] actual = out.toByteArray();
		// Check that the actual ciphertext matches the expected ciphertext
		assertArrayEquals(expected, actual);
		assertEquals(Long.MAX_VALUE - actual.length, e.getRemainingCapacity());
	}
}
