/*
 * Copyright (c) 2003-2006 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.jme.scene.state.gdx;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.logging.Level;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBBufferObject;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexBufferObject;
import org.lwjgl.opengl.ARBVertexProgram;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import com.jme.renderer.RenderContext;
import com.jme.scene.state.GLSLShaderObjectsState;
import com.jme.scene.state.StateRecord;
import com.jme.scene.state.gdx.records.ShaderObjectsStateRecord;
import com.jme.system.DisplaySystem;
import com.jme.util.LoggingSystem;
import com.jme.util.ShaderAttribute;
import com.jme.util.ShaderUniform;

/**
 * Implementation of the GL_ARB_shader_objects extension.
 *
 * @author Thomas Hourdel
 * @author Joshua Slack (attributes and StateRecord)
 */
public class GDXShaderObjectsState extends GLSLShaderObjectsState {

    private static final long serialVersionUID = 1L;

    /**
     * Determines if the current OpenGL context supports the
     * GL_ARB_shader_objects extension.
     *
     * @see com.jme.scene.state.ShaderObjectsState#isSupported()
     */
    @Override
	public boolean isSupported() {
        return GLContext.getCapabilities().GL_ARB_shader_objects;
    }

    /**
     * <code>relinkProgram</code> instructs openGL to relink the associated
     * program.  This should be used after setting ShaderAttributes.
     */
    @Override
	public void relinkProgram() {
        ByteBuffer nameBuf = BufferUtils.createByteBuffer(64);
        int id = 0;
        for (ShaderAttribute attrib : attribs.values()) {
            nameBuf.clear();
            nameBuf.put(attrib.name.getBytes());
            nameBuf.rewind();
            attrib.attributeID = ++id;
            ARBVertexShader.glBindAttribLocationARB(programID, id, nameBuf);
        }

        ARBShaderObjects.glLinkProgramARB(programID);
    }

    /**
     * Get attribute variable location according to his string name.
     *
     * @param name
     *            attribute variable name
     */
    private int getAttrLoc(ShaderAttribute attribute) {
        if (attribute.attributeID == -1) {
            ByteBuffer nameBuf = BufferUtils
            	.createByteBuffer(attribute.name.getBytes().length+1);
            nameBuf.clear();
            nameBuf.put(attribute.name.getBytes());
            nameBuf.rewind();

            attribute.attributeID = ARBVertexShader.glGetAttribLocationARB(programID, nameBuf);
        }
        return attribute.attributeID;
    }

    /**
     * Get uniform variable location according to his string name.
     *
     * @param name
     *            uniform variable name
     */
    private int getUniLoc(ShaderUniform uniform) {
        if (uniform.uniformID == -1) {
            ByteBuffer nameBuf = BufferUtils
            	.createByteBuffer(uniform.name.getBytes().length+1);
            nameBuf.clear();
            nameBuf.put(uniform.name.getBytes());
            nameBuf.rewind();

            uniform.uniformID = ARBShaderObjects.glGetUniformLocationARB(programID, nameBuf);
        }
        return uniform.uniformID;
    }

    /**
     * Load an URL and grab content into a ByteBuffer.
     *
     * @param url
     *            the url to load
     */

    private ByteBuffer load(java.net.URL url) {
        try {
            byte shaderCode[] = null;
            ByteBuffer shaderByteBuffer = null;

            BufferedInputStream bufferedInputStream = new BufferedInputStream(
                    url.openStream());
            DataInputStream dataStream = new DataInputStream(
                    bufferedInputStream);
            dataStream.readFully(shaderCode = new byte[bufferedInputStream
                    .available()]);
            bufferedInputStream.close();
            dataStream.close();
            shaderByteBuffer = BufferUtils.createByteBuffer(shaderCode.length);
            shaderByteBuffer.put(shaderCode);
            shaderByteBuffer.rewind();

            return shaderByteBuffer;
        } catch (Exception e) {
            LoggingSystem.getLogger().log(Level.SEVERE,
                    "Could not load shader object: " + e);
            LoggingSystem.getLogger().throwing(getClass().getName(),
                    "load(URL)", e);
            return null;
        }
    }

    private ByteBuffer load(String data) {
        try {
            byte[] bytes = data.getBytes();
            ByteBuffer program = BufferUtils.createByteBuffer(bytes.length);
            program.put(bytes);
            program.rewind();
            return program;
        } catch (Exception e) {
            LoggingSystem.getLogger().log(Level.SEVERE,
                    "Could not load fragment program: " + e);
            LoggingSystem.getLogger().throwing(getClass().getName(),
                    "load(URL)", e);
            return null;
        }
    }

    /**
     * Loads the shader object. Use null for an empty vertex or empty fragment shader.
     *
     * @see com.jme.scene.state.ShaderObjectsState#load(java.net.URL,
     *      java.net.URL)
     */
    @Override
	public void load(URL vert, URL frag) {
        ByteBuffer vertexByteBuffer = vert != null ? load(vert) : null;
        ByteBuffer fragmentByteBuffer = frag!= null ? load(frag) : null;
        load(vertexByteBuffer, fragmentByteBuffer);
    }

    @Override
	public void load(String vert, String frag) {
        ByteBuffer vertexByteBuffer = vert != null ? load(vert) : null;
        ByteBuffer fragmentByteBuffer = frag!= null ? load(frag) : null;
        load(vertexByteBuffer, fragmentByteBuffer);
    }

    private void load(ByteBuffer vertexByteBuffer, ByteBuffer fragmentByteBuffer) {

        if (vertexByteBuffer == null && fragmentByteBuffer == null) {
            LoggingSystem.getLogger().log(Level.WARNING, "Could not find shader resources! (both inputbuffers are null)");
            return;
        }

        programID = ARBShaderObjects.glCreateProgramObjectARB();

        if (vertexByteBuffer != null) {
            int vertexShaderID = ARBShaderObjects.glCreateShaderObjectARB(ARBVertexShader.GL_VERTEX_SHADER_ARB);

            // Create the sources
            ARBShaderObjects.glShaderSourceARB(vertexShaderID, vertexByteBuffer);

            // Compile the vertex shader
            IntBuffer compiled = BufferUtils.createIntBuffer(1);
            ARBShaderObjects.glCompileShaderARB(vertexShaderID);
            ARBShaderObjects.glGetObjectParameterARB(vertexShaderID, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB, compiled);
            checkProgramError(compiled, vertexShaderID);

            // Attatch the program
            ARBShaderObjects.glAttachObjectARB(programID, vertexShaderID);
        }

        if (fragmentByteBuffer != null) {
            int fragmentShaderID = ARBShaderObjects.glCreateShaderObjectARB(ARBFragmentShader.GL_FRAGMENT_SHADER_ARB);

            // Create the sources
            ARBShaderObjects.glShaderSourceARB(fragmentShaderID, fragmentByteBuffer);

            // Compile the fragment shader
            IntBuffer compiled = BufferUtils.createIntBuffer(1);
            ARBShaderObjects.glCompileShaderARB(fragmentShaderID);
            ARBShaderObjects.glGetObjectParameterARB(fragmentShaderID, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB, compiled);
            checkProgramError(compiled, fragmentShaderID);

            // Attatch the program
            ARBShaderObjects.glAttachObjectARB(programID, fragmentShaderID);
        }

        ARBShaderObjects.glLinkProgramARB(programID);
        setNeedsRefresh(true);
    }

    /**
     * Check for program errors. If an error is detected, program exits.
     *
     * @param compiled
     *            the compiler state for a given shader
     * @param id
     *            shader's id
     */
    private void checkProgramError(IntBuffer compiled, int id) {

        if (compiled.get(0) == 0) {
            IntBuffer iVal = BufferUtils.createIntBuffer(1);
            ARBShaderObjects.glGetObjectParameterARB(id,
                    ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB, iVal);
            int length = iVal.get();
            String out = null;

            if (length > 0) {
                ByteBuffer infoLog = BufferUtils.createByteBuffer(length);

                iVal.flip();
                ARBShaderObjects.glGetInfoLogARB(id, iVal, infoLog);

                byte[] infoBytes = new byte[length];
                infoLog.get(infoBytes);
                out = new String(infoBytes);
            }

            LoggingSystem.getLogger().log(Level.SEVERE, out);
        }
    }

    /**
     * Applies those shader objects to the current scene. Checks if the
     * GL_ARB_shader_objects extension is supported before attempting to enable
     * those objects.
     *
     * @see com.jme.scene.state.RenderState#apply()
     */
    @Override
	public void apply() {
        if (isSupported()) {
        	//ask for the current state record
            RenderContext context = DisplaySystem.getDisplaySystem()
                    .getCurrentContext();
            ShaderObjectsStateRecord record = (ShaderObjectsStateRecord) context
                    .getStateRecord(RS_GLSL_SHADER_OBJECTS);
            context.currentStates[RS_GLSL_SHADER_OBJECTS] = this;

            if (record.getReference() != this) {
            	record.setReference(this);

            	// disable any currently enabled vertex attrib arrays
                for (int attributeID : record.attribArrays) {
                    ARBVertexProgram.glDisableVertexAttribArrayARB(attributeID);
                }
                record.attribArrays.clear();

                if (isEnabled()) {
                	if (programID != -1) {
                	    boolean supportsVBO =
                	        DisplaySystem.getDisplaySystem().getRenderer().supportsVBO();
                        boolean usingVBO = false;

                        // Apply the shader...
                        ARBShaderObjects.glUseProgramObjectARB(programID);

                        // Assign attribs...
                        if (!attribs.isEmpty()) {
                            for (ShaderAttribute attVar : attribs.values()) {
                                if (supportsVBO && attVar.useVBO && attVar.data != null) {
                                    boolean populate = false;
                                    if (attVar.vboID <= 0) {
                                        IntBuffer buf = BufferUtils.createIntBuffer(1);
                                        ARBBufferObject.glGenBuffersARB(buf);
                                        attVar.vboID = buf.get(0);
                                        populate = true;
                                    }
                                    ARBBufferObject.glBindBufferARB(
                                        ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,
                                        attVar.vboID);
                                    usingVBO = true;
                                    if (populate) {
                                        int target = ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,
                                            usage = ARBBufferObject.GL_STATIC_DRAW_ARB;
                                        if (attVar.type == ShaderAttribute.SU_POINTER_FLOAT) {
                                            ARBBufferObject.glBufferDataARB(
                                                target, (FloatBuffer)attVar.data, usage);
                                        } else if (attVar.type == ShaderAttribute.SU_POINTER_BYTE) {
                                            ARBBufferObject.glBufferDataARB(
                                                target, (ByteBuffer)attVar.data, usage);
                                        } else if (attVar.type == ShaderAttribute.SU_POINTER_INT) {
                                            ARBBufferObject.glBufferDataARB(
                                                target, (IntBuffer)attVar.data, usage);
                                        } else { // attVar.type == ShaderAttribute.SU_POINTER_SHORT
                                            ARBBufferObject.glBufferDataARB(
                                                target, (ShortBuffer)attVar.data, usage);
                                        }
                                    }
                                    ARBVertexShader.glVertexAttribPointerARB(
                                        getAttrLoc(attVar),
                                        attVar.size,
                                        getGLType(attVar.type, attVar.unsigned),
                                        attVar.normalized,
                                        attVar.stride,
                                        0L);
                                    ARBVertexProgram.glEnableVertexAttribArrayARB(attVar.attributeID);
                                    record.attribArrays.add(attVar.attributeID);
                                    continue;

                                } else if (usingVBO) {
                                    ARBBufferObject.glBindBufferARB(
                                        ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, 0);
                                }

                                switch (attVar.type) {
                                case ShaderAttribute.SU_SHORT:
                                    ARBVertexProgram.glVertexAttrib1sARB(
                                            getAttrLoc(attVar),
                                            attVar.s1);
                                    break;
                                case ShaderAttribute.SU_SHORT2:
                                    ARBVertexProgram.glVertexAttrib2sARB(
                                            getAttrLoc(attVar),
                                            attVar.s1, attVar.s2);
                                    break;
                                case ShaderAttribute.SU_SHORT3:
                                    ARBVertexProgram.glVertexAttrib3sARB(
                                            getAttrLoc(attVar),
                                            attVar.s1, attVar.s2,
                                            attVar.s3);
                                    break;
                                case ShaderAttribute.SU_SHORT4:
                                    ARBVertexProgram.glVertexAttrib4sARB(
                                            getAttrLoc(attVar),
                                            attVar.s1, attVar.s2,
                                            attVar.s3, attVar.s4);
                                    break;
                                case ShaderAttribute.SU_FLOAT:
                                    ARBVertexProgram.glVertexAttrib1fARB(
                                            getAttrLoc(attVar),
                                            attVar.f1);
                                    break;
                                case ShaderAttribute.SU_FLOAT2:
                                    ARBVertexProgram.glVertexAttrib2fARB(
                                            getAttrLoc(attVar),
                                            attVar.f1, attVar.f2);
                                    break;
                                case ShaderAttribute.SU_FLOAT3:
                                    ARBVertexProgram.glVertexAttrib3fARB(
                                            getAttrLoc(attVar),
                                            attVar.f1, attVar.f2,
                                            attVar.f3);
                                    break;
                                case ShaderAttribute.SU_FLOAT4:
                                    ARBVertexProgram.glVertexAttrib4fARB(
                                            getAttrLoc(attVar),
                                            attVar.f1, attVar.f2,
                                            attVar.f3, attVar.f4);
                                    break;
                                case ShaderAttribute.SU_NORMALIZED_UBYTE4:
                                    ARBVertexProgram.glVertexAttrib4NubARB(
                                            getAttrLoc(attVar),
                                            attVar.b1, attVar.b2,
                                            attVar.b3, attVar.b4);
                                    break;
                                case ShaderAttribute.SU_POINTER_FLOAT:
                                    ARBVertexProgram.glVertexAttribPointerARB(
                                            getAttrLoc(attVar),
                                            attVar.size,
                                            attVar.normalized,
                                            attVar.stride,
                                            (FloatBuffer)attVar.data);
                                    ARBVertexProgram.glEnableVertexAttribArrayARB(attVar.attributeID);
                                    record.attribArrays.add(attVar.attributeID);
                                    break;
                                case ShaderAttribute.SU_POINTER_BYTE:
                                    ARBVertexProgram.glVertexAttribPointerARB(
                                            getAttrLoc(attVar),
                                            attVar.size,
                                            attVar.unsigned,
                                            attVar.normalized,
                                            attVar.stride,
                                            (ByteBuffer)attVar.data);
                                    ARBVertexProgram.glEnableVertexAttribArrayARB(attVar.attributeID);
                                    record.attribArrays.add(attVar.attributeID);
                                    break;
                                case ShaderAttribute.SU_POINTER_INT:
                                    ARBVertexProgram.glVertexAttribPointerARB(
                                            getAttrLoc(attVar),
                                            attVar.size,
                                            attVar.unsigned,
                                            attVar.normalized,
                                            attVar.stride,
                                            (IntBuffer)attVar.data);
                                    ARBVertexProgram.glEnableVertexAttribArrayARB(attVar.attributeID);
                                    record.attribArrays.add(attVar.attributeID);
                                    break;
                                case ShaderAttribute.SU_POINTER_SHORT:
                                    ARBVertexProgram.glVertexAttribPointerARB(
                                            getAttrLoc(attVar),
                                            attVar.size,
                                            attVar.unsigned,
                                            attVar.normalized,
                                            attVar.stride,
                                            (ShortBuffer)attVar.data);
                                    ARBVertexProgram.glEnableVertexAttribArrayARB(attVar.attributeID);
                                    record.attribArrays.add(attVar.attributeID);
                                    break;
                                default: // Should never happen.
                                    break;
                                }
                            }
                        }
                        if (usingVBO) {
                            ARBBufferObject.glBindBufferARB(
                                ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, 0);
                        }

                        // Assign uniforms...
                        if (!uniforms.isEmpty()) {
                            for (ShaderUniform uniformVar : uniforms.values()) {
                                switch (uniformVar.type) {
                                case ShaderUniform.SU_INT:
                                    ARBShaderObjects.glUniform1iARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.vint[0]);
                                    break;
                                case ShaderUniform.SU_INT2:
                                    ARBShaderObjects.glUniform2iARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.vint[0], uniformVar.vint[1]);
                                    break;
                                case ShaderUniform.SU_INT3:
                                    ARBShaderObjects.glUniform3iARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.vint[0], uniformVar.vint[1],
                                            uniformVar.vint[2]);
                                    break;
                                case ShaderUniform.SU_INT4:
                                    ARBShaderObjects.glUniform4iARB(
                                            getUniLoc(uniformVar),

                                            uniformVar.vint[0], uniformVar.vint[1],
                                            uniformVar.vint[2], uniformVar.vint[3]);
                                    break;
                                case ShaderUniform.SU_FLOAT:
                                    ARBShaderObjects.glUniform1fARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.vfloat[0]);
                                    break;
                                case ShaderUniform.SU_FLOAT2:
                                    ARBShaderObjects.glUniform2fARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.vfloat[0],
                                            uniformVar.vfloat[1]);
                                    break;
                                case ShaderUniform.SU_FLOAT3:
                                    ARBShaderObjects.glUniform3fARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.vfloat[0],
                                            uniformVar.vfloat[1],
                                            uniformVar.vfloat[2]);
                                    break;
                                case ShaderUniform.SU_FLOAT4:
                                    ARBShaderObjects.glUniform4fARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.vfloat[0],
                                            uniformVar.vfloat[1],
                                            uniformVar.vfloat[2],
                                            uniformVar.vfloat[3]);
                                    break;
                                case ShaderUniform.SU_MATRIX2:
                                    if (uniformVar.matrixBuffer == null)
                                        uniformVar.matrixBuffer = org.lwjgl.BufferUtils.createFloatBuffer(4);
                                    uniformVar.matrixBuffer.clear();
                                    uniformVar.matrixBuffer.put(uniformVar.matrix2f);
                                    uniformVar.matrixBuffer.rewind();
                                    ARBShaderObjects.glUniformMatrix2ARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.transpose, uniformVar.matrixBuffer);
                                    break;
                                case ShaderUniform.SU_MATRIX3:
                                    if (uniformVar.matrixBuffer == null)
                                        uniformVar.matrixBuffer = uniformVar.matrix3f.toFloatBuffer();
                                    else
                                        uniformVar.matrix3f.fillFloatBuffer(uniformVar.matrixBuffer);
                                    ARBShaderObjects.glUniformMatrix3ARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.transpose,
                                            uniformVar.matrixBuffer);
                                    break;
                                case ShaderUniform.SU_MATRIX4:
                                    if (uniformVar.matrixBuffer == null)
                                        uniformVar.matrixBuffer = uniformVar.matrix4f.toFloatBuffer();
                                    else
                                        uniformVar.matrix4f.fillFloatBuffer(uniformVar.matrixBuffer);
                                    ARBShaderObjects.glUniformMatrix4ARB(
                                            getUniLoc(uniformVar),
                                            uniformVar.transpose,
                                            uniformVar.matrixBuffer);
                                    break;
                                default: // Should never happen.
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    ARBShaderObjects.glUseProgramObjectARB(0);
                }
            }
        }
    }

    protected int getGLType (int attrType, boolean unsigned)
    {
        switch (attrType) {
            case ShaderAttribute.SU_POINTER_BYTE:
                return (unsigned ? GL11.GL_UNSIGNED_BYTE : GL11.GL_BYTE);
            case ShaderAttribute.SU_POINTER_FLOAT:
                return GL11.GL_FLOAT;
            case ShaderAttribute.SU_POINTER_INT:
                return (unsigned ? GL11.GL_UNSIGNED_INT : GL11.GL_INT);
            case ShaderAttribute.SU_POINTER_SHORT:
                return (unsigned ? GL11.GL_UNSIGNED_SHORT : GL11.GL_SHORT);
            default:
                return -1;
        }
    }

    @Override
    public StateRecord createStateRecord() {
    	return new ShaderObjectsStateRecord();
    }
}
