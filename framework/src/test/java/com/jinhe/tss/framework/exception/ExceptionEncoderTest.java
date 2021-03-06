package com.jinhe.tss.framework.exception;

import javax.servlet.ServletResponse;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jinhe.tss.framework.sso.context.Context;

public class ExceptionEncoderTest {
	
	@Test
	public void test() {
		ServletResponse response = new MockHttpServletResponse();
        Context.initRequestContext(new MockHttpServletRequest());
		
		Exception be = new BusinessException("test BusinessException encoder");
		ExceptionEncoder.encodeException(response, be);
		
		be = new BusinessServletException(be, false);
		ExceptionEncoder.encodeException(response, be);
		
		be = new BusinessServletException(be, true);
		ExceptionEncoder.encodeException(response, be);
	}

}
