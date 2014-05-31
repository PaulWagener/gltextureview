package com.eaglesakura.view.egl;

import static javax.microedition.khronos.egl.EGL10.EGL_HEIGHT;
import static javax.microedition.khronos.egl.EGL10.EGL_NONE;
import static javax.microedition.khronos.egl.EGL10.EGL_WIDTH;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL11;

import android.graphics.SurfaceTexture;
import android.os.Looper;

import com.eaglesakura.view.GLTextureView.EGLConfigChooser;
import com.eaglesakura.view.GLTextureView.GLESVersion;

public class EGLManager {
	/**
	 * ���������������������������
	 */
	final private Object lock = new Object();

	/**
	 * EGL������������������
	 */
	EGL10 egl = null;

	/**
	 * ���������������������������������������
	 */
	EGLDisplay eglDisplay = null;

	/**
	 * ���������������������������������������
	 */
	EGLSurface eglSurface = null;

	/**
	 * ���������������������������������������
	 */
	EGLContext eglContext = null;

	/**
	 * config������
	 */
	EGLConfig eglConfig = null;

	/**
	 * ���������������������������������������������������EGLDisplay������������
	 * ������
	 */
	EGLDisplay systemDisplay = null;

	/**
	 * ���������������������������������������������������EGLSurface(Read)
	 */
	EGLSurface systemReadSurface = null;

	/**
	 * ���������������������������������������������������EGLSurface(Draw)
	 */
	EGLSurface systemDrawSurface = null;

	/**
	 * ���������������������������������������������������������������������
	 */
	EGLContext systemContext = null;

	/**
	 * GL10 object only OpenGL ES 1.1
	 */
	GL11 gl11 = null;

	public EGLManager() {
	}

	/**
	 * ������������������
	 */
	public void initialize(final EGLConfigChooser chooser,
			final GLESVersion version) {
		synchronized (lock) {
			if (egl != null) {
				throw new RuntimeException("initialized");
			}

			egl = (EGL10) EGLContext.getEGL();

			// ���������������������������������������������������������������
			{
				systemDisplay = egl.eglGetCurrentDisplay();
				systemReadSurface = egl.eglGetCurrentSurface(EGL10.EGL_READ);
				systemDrawSurface = egl.eglGetCurrentSurface(EGL10.EGL_DRAW);
				systemContext = egl.eglGetCurrentContext();
			}

			// ������������������������
			{
				eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
				if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
					throw new RuntimeException("EGL_NO_DISPLAY");
				}

				if (!egl.eglInitialize(eglDisplay, new int[2])) {
					throw new RuntimeException("eglInitialize");
				}
			}
			// ���������������������
			{
				eglConfig = chooser.chooseConfig(egl, eglDisplay, version);
				if (eglConfig == null) {
					throw new RuntimeException("chooseConfig");
				}
			}

			// ������������������������
			{
				eglContext = egl.eglCreateContext(eglDisplay, eglConfig,
						EGL10.EGL_NO_CONTEXT, version.getContextAttributes());

				if (eglContext == EGL10.EGL_NO_CONTEXT) {
					throw new RuntimeException("eglCreateContext");
				}
			}

			if (version == GLESVersion.OpenGLES11) {
				gl11 = (GL11) eglContext.getGL();
			}
		}
	}

	/**
	 * get GL11 object
	 * 
	 * @return
	 */
	public GL11 getGL11() {
		if (gl11 == null) {
			throw new UnsupportedOperationException("OpenGL ES 1.1 only");
		}
		return gl11;
	}

	public EGLConfig getConfig() {
		return eglConfig;
	}

	public EGLSurface getSurface() {
		return eglSurface;
	}

	public EGLContext getContext() {
		return eglContext;
	}

	/**
	 * ���������������������
	 * 
	 * @param view
	 */
	public void resize(SurfaceTexture surface) {
		synchronized (lock) {
			// ������������������������������������������������������������
			if (eglSurface != null) {
				egl.eglDestroySurface(eglDisplay, eglSurface);
			}

			// ������������������������������������������������������
			{
				eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig,
						surface, null);
				if (eglSurface == EGL10.EGL_NO_SURFACE) {
					throw new RuntimeException("eglCreateWindowSurface");
				}
			}
		}
	}

	public void pbuffer(int width, int height) {
		int[] attribList = new int[] { EGL_WIDTH, width, EGL_HEIGHT, height,
				EGL_NONE };
		eglSurface = egl.eglCreatePbufferSurface(eglDisplay, eglConfig,
				attribList);
		if (eglSurface == EGL10.EGL_NO_SURFACE) {
			throw new RuntimeException("eglCreatePbufferSurface");
		}
	}

	/**
	 * ���������������������
	 */
	public void destroy() {
		synchronized (lock) {
			if (egl == null) {
				return;
			}

			if (eglSurface != null) {
				egl.eglDestroySurface(eglDisplay, eglSurface);
				eglSurface = null;
			}
			if (eglContext != null) {
				egl.eglDestroyContext(eglDisplay, eglContext);
				eglContext = null;
			}
			eglConfig = null;
			egl = null;
		}
	}

	/**
	 * ES20���������������������������������
	 */
	public void bind() {
		synchronized (lock) {
			egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
		}
	}

	/**
	 * UI���������������������������������������true������������
	 * 
	 * @return
	 */
	public boolean isUIThread() {
		return Thread.currentThread()
				.equals(Looper.getMainLooper().getThread());
	}

	/**
	 * ������������������������������������������
	 */
	public void unbind() {
		synchronized (lock) {
			if (isUIThread()) {
				// UI������������������������������������������������������������
				egl.eglMakeCurrent(systemDisplay, systemDrawSurface,
						systemReadSurface, systemContext);
			} else {
				// ������������������������null���������������
				egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE,
						EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
			}
		}
	}

	/**
	 * ������������������������������������������NO_SURFACE������������������
	 */
	public void releaseThread() {
		synchronized (lock) {
			egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE,
					EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
		}
	}

	/**
	 * ������������������������������������������������������������������
	 */
	public boolean swapBuffers() {
		synchronized (lock) {
			return egl.eglSwapBuffers(eglDisplay, eglSurface);
		}
	}
}
