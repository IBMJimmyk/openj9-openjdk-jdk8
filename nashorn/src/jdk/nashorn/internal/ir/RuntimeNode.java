/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.internal.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for a runtime call.
 *
 */
public class RuntimeNode extends Node implements TypeOverride {

    /**
     * Request enum used for meta-information about the runtime request
     */
    public enum Request {
        /** An addition with at least one object */
        ADD(TokenType.ADD, Type.OBJECT, 2, true),
        /** Request to enter debugger */
        DEBUGGER,
        /** New operator */
        NEW,
        /** Typeof operator */
        TYPEOF,
        /** void type */
        VOID,
        /** Reference error type */
        REFERENCE_ERROR,
        /** Delete operator */
        DELETE(TokenType.DELETE, Type.BOOLEAN, 1),
        /** Delete operator that always fails -- see Lower */
        FAIL_DELETE(TokenType.DELETE, Type.BOOLEAN, 1, false),
        /** === operator with at least one object */
        EQ_STRICT(TokenType.EQ_STRICT, Type.BOOLEAN, 2, true),
        /** == operator with at least one object */
        EQ(TokenType.EQ, Type.BOOLEAN, 2, true),
        /** >= operator with at least one object */
        GE(TokenType.GE, Type.BOOLEAN, 2, true),
        /** > operator with at least one object */
        GT(TokenType.GT, Type.BOOLEAN, 2, true),
        /** in operator */
        IN(TokenType.IN, Type.BOOLEAN, 2),
        /** instanceof operator */
        INSTANCEOF(TokenType.INSTANCEOF, Type.BOOLEAN, 2),
        /** <= operator with at least one object */
        LE(TokenType.LE, Type.BOOLEAN, 2, true),
        /** < operator with at least one object */
        LT(TokenType.LT, Type.BOOLEAN, 2, true),
        /** !== operator with at least one object */
        NE_STRICT(TokenType.NE_STRICT, Type.BOOLEAN, 2, true),
        /** != operator with at least one object */
        NE(TokenType.NE, Type.BOOLEAN, 2, true);

        /** token type */
        private final TokenType tokenType;

        /** return type for request */
        private final Type returnType;

        /** arity of request */
        private final int arity;

        /** Can the specializer turn this into something that works with 1 or more primitives? */
        private final boolean canSpecialize;

        private Request() {
            this(TokenType.VOID, Type.OBJECT, 0);
        }

        private Request(final TokenType tokenType, final Type returnType, final int arity) {
            this(tokenType, returnType, arity, false);
        }

        private Request(final TokenType tokenType, final Type returnType, final int arity, final boolean canSpecialize) {
            this.tokenType     = tokenType;
            this.returnType    = returnType;
            this.arity         = arity;
            this.canSpecialize = canSpecialize;
        }

        /**
         * Can this request type be specialized?
         *
         * @return true if request can be specialized
         */
        public boolean canSpecialize() {
            return canSpecialize;
        }

        /**
         * Get arity
         *
         * @return the arity of the request
         */
        public int getArity() {
            return arity;
        }

        /**
         * Get the return type
         *
         * @return return type for request
         */
        public Type getReturnType() {
            return returnType;
        }

        /**
         * Get token type
         *
         * @return token type for request
         */
        public TokenType getTokenType() {
            return tokenType;
        }

        /**
         * Is this an EQ or EQ_STRICT?
         *
         * @param request a request
         *
         * @return true if EQ or EQ_STRICT
         */
        public static boolean isEQ(final Request request) {
            return request == EQ || request == EQ_STRICT;
        }

        /**
         * Is this an NE or NE_STRICT?
         *
         * @param request a request
         *
         * @return true if NE or NE_STRICT
         */
        public static boolean isNE(final Request request) {
            return request == NE || request == NE_STRICT;
        }

        /**
         * If this request can be reversed, return the reverse request
         * Eq EQ -> NE.
         *
         * @param request request to reverse
         *
         * @return reversed request or null if not applicable
         */
        public static Request reverse(final Request request) {
            switch (request) {
            case EQ:
            case EQ_STRICT:
            case NE:
            case NE_STRICT:
                return request;
            case LE:
                return GE;
            case LT:
                return GT;
            case GE:
                return LE;
            case GT:
                return LT;
            default:
                return null;
            }
        }

        /**
         * Invert the request, only for non equals comparisons.
         *
         * @param request a request
         *
         * @return the inverted rquest, or null if not applicable
         */
        public static Request invert(final Request request) {
            switch (request) {
            case EQ:
                return NE;
            case EQ_STRICT:
                return NE_STRICT;
            case NE:
                return EQ;
            case NE_STRICT:
                return EQ_STRICT;
            case LE:
                return GT;
            case LT:
                return GE;
            case GE:
                return LT;
            case GT:
                return LE;
            default:
                return null;
            }
        }

        /**
         * Check if this is a comparison
         *
         * @param request a request
         *
         * @return true if this is a comparison, null otherwise
         */
        public static boolean isComparison(final Request request) {
            switch (request) {
            case EQ:
            case EQ_STRICT:
            case NE:
            case NE_STRICT:
            case LE:
            case LT:
            case GE:
            case GT:
                return true;
            default:
                return false;
            }
        }
    }

    /** Runtime request. */
    private final Request request;

    /** Call arguments. */
    private final List<Node> args;

    /** Call site override - e.g. we know that a ScriptRuntime.ADD will return an int */
    private Type callSiteType;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param request the request
     * @param args    arguments to request
     */
    public RuntimeNode(final Source source, final long token, final int finish, final Request request, final List<Node> args) {
        super(source, token, finish);

        this.request      = request;
        this.args         = args;
    }

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param request the request
     * @param args    arguments to request
     */
    public RuntimeNode(final Source source, final long token, final int finish, final Request request, final Node... args) {
        this(source, token, finish, request, Arrays.asList(args));
    }

    private RuntimeNode(final RuntimeNode runtimeNode, final CopyState cs) {
        super(runtimeNode);

        final List<Node> newArgs = new ArrayList<>();

        for (final Node arg : runtimeNode.args) {
            newArgs.add(cs.existingOrCopy(arg));
        }

        this.request      = runtimeNode.request;
        this.args         = newArgs;
        this.callSiteType = runtimeNode.callSiteType;
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new RuntimeNode(this, cs);
    }

    /**
     * Return type for the ReferenceNode
     */
    @Override
    public Type getType() {
        return hasCallSiteType() ? callSiteType : request.getReturnType();
    }

    @Override
    public void setType(final Type type) {
        this.callSiteType = type;
    }

    @Override
    public boolean canHaveCallSiteType() {
        return request == Request.ADD;
    }

    private boolean hasCallSiteType() {
        return callSiteType != null;
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            for (int i = 0, count = args.size(); i < count; i++) {
                args.set(i, args.get(i).accept(visitor));
            }

            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("ScriptRuntime.");
        sb.append(request);
        sb.append('(');

        boolean first = true;

        for (final Node arg : args) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            arg.toString(sb);
        }

        sb.append(')');
    }

    /**
     * Get the arguments for this runtime node
     * @return argument list
     */
    public List<Node> getArgs() {
        return Collections.unmodifiableList(args);
    }

    /**
     * Get the request that this runtime node implements
     * @return the request
     */
    public Request getRequest() {
        return request;
    }

    /**
     * Is this runtime node, engineered to handle the "at least one object" case of the defined
     * requests and specialize on demand, really primitive. This can happen e.g. after AccessSpecializer
     * In that case it can be turned into a simpler primitive form in CodeGenerator
     *
     * @return true if all arguments now are primitive
     */
    public boolean isPrimitive() {
        for (final Node arg : args) {
            if (arg.getType().isObject()) {
                return false;
            }
        }
        return true;
    }
}
