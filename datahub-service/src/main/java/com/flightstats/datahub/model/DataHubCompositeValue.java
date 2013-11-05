package com.flightstats.datahub.model;

import com.google.common.base.Optional;

import java.io.Serializable;
import java.util.Arrays;

public class DataHubCompositeValue implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Optional<String> contentType;
	private final Optional<String> contentLanguage;
	private final byte[] data;

    public DataHubCompositeValue(Optional<String> contentType, Optional<String> contentLanguage, byte[] data) {
        this.contentType = contentType;
		this.contentLanguage = contentLanguage;
		this.data = data;
    }

    public Optional<String> getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

	public int getDataLength() {
		return data == null ? 0 : data.length;
	}

	public Optional<String> getContentLanguage() {
		return contentLanguage;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		DataHubCompositeValue that = (DataHubCompositeValue) o;

		if (!contentLanguage.equals(that.contentLanguage)) {
			return false;
		}
		if (!contentType.equals(that.contentType)) {
			return false;
		}
		if (!Arrays.equals(data, that.data)) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = contentType.hashCode();
		result = 31 * result + contentLanguage.hashCode();
		result = 31 * result + (data != null ? Arrays.hashCode(data) : 0);
		return result;
	}
}
