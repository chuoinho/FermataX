package me.aap.fermata.addon.audiobook.scan;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Small seekable reader for regular files and seekable SAF documents. */
abstract class SeekableMediaInput implements Closeable {
	static SeekableMediaInput open(Context context, Uri uri) throws IOException {
		if ("file".equalsIgnoreCase(uri.getScheme())) {
			String path = uri.getPath();
			if (path == null) throw new IOException("File URI has no path");
			return new FileInput(new File(path));
		}
		ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
		if (descriptor == null) throw new IOException("Unable to open media URI");
		return new DescriptorInput(descriptor);
	}

	abstract long length() throws IOException;

	abstract int read(long position, byte[] target, int offset, int length) throws IOException;

	final void readFully(long position, byte[] target) throws IOException {
		readFully(position, target, 0, target.length);
	}

	final void readFully(long position, byte[] target, int offset, int length) throws IOException {
		int read = 0;
		while (read < length) {
			int count = read(position + read, target, offset + read, length - read);
			if (count < 0) throw new EOFException("Unexpected end of media data");
			if (count == 0) throw new IOException("Media source made no read progress");
			read += count;
		}
	}

	private static final class FileInput extends SeekableMediaInput {
		private final RandomAccessFile file;

		private FileInput(File path) throws IOException {
			file = new RandomAccessFile(path, "r");
		}

		@Override
		long length() throws IOException {
			return file.length();
		}

		@Override
		int read(long position, byte[] target, int offset, int length) throws IOException {
			file.seek(position);
			return file.read(target, offset, length);
		}

		@Override
		public void close() throws IOException {
			file.close();
		}
	}

	private static final class DescriptorInput extends SeekableMediaInput {
		private final ParcelFileDescriptor descriptor;
		private final FileInputStream stream;
		private final FileChannel channel;

		private DescriptorInput(ParcelFileDescriptor descriptor) {
			this.descriptor = descriptor;
			stream = new FileInputStream(descriptor.getFileDescriptor());
			channel = stream.getChannel();
		}

		@Override
		long length() throws IOException {
			long statSize = descriptor.getStatSize();
			return (statSize >= 0) ? statSize : channel.size();
		}

		@Override
		int read(long position, byte[] target, int offset, int length) throws IOException {
			return channel.read(ByteBuffer.wrap(target, offset, length), position);
		}

		@Override
		public void close() throws IOException {
			IOException failure = null;
			try {
				stream.close();
			} catch (IOException ex) {
				failure = ex;
			}
			try {
				descriptor.close();
			} catch (IOException ex) {
				if (failure == null) failure = ex;
			}
			if (failure != null) throw failure;
		}
	}
}
