/*	VIDIS is a simulation and visualisation framework for distributed systems.
	Copyright (C) 2009 Dominik Psenner, Christoph Caks
	This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
	You should have received a copy of the GNU General Public License along with this program; if not, see <http://www.gnu.org/licenses/>. */
package vidis.data.var.vars;

import java.util.ArrayList;
import java.util.List;

import vidis.data.var.IVariable;
import vidis.data.var.IVariableChangeListener;

public abstract class AVariable implements IVariable {
	public static final class COMMON_SCOPES {
		public static final String USER = "user";
		public static final String SYSTEM = "system";
	}
	public static final class COMMON_IDENTIFIERS {
		public static final String ID = COMMON_SCOPES.SYSTEM + ".id";
		public static final String COLOR = COMMON_SCOPES.SYSTEM + ".color";
		public static final String POSITION = COMMON_SCOPES.SYSTEM + ".position";
		public static final String PACKETSSENT = COMMON_SCOPES.SYSTEM + ".packetsSent";
		public static final String PACKETSRECEIVED = COMMON_SCOPES.SYSTEM + ".packetsReceived";
		public static final String NAME = COMMON_SCOPES.USER + ".name";
		public static final String PACKETDIRECTION = COMMON_SCOPES.SYSTEM + ".packetDirection";
	}
	private String identifier;
//	private DisplayType displayType = DisplayType.SHOW_SWING;
	private List<IVariableChangeListener> variableChangeListeners;
	private AVariable() {
		this.variableChangeListeners = new ArrayList<IVariableChangeListener>();
	}
	public AVariable(String id) {
		this();
		setIdentifier(id);
	}
	
	/**
	 * retrieve the identifier of this variable
	 * 
	 * @return string identifier
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * set the identifier for this class
	 * 
	 * @param identifier
	 *          identifier
	 */
	private void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
	
	public void removeVariableChangeListener(IVariableChangeListener l) {
		variableChangeListeners.remove(l);
	}

	public void addVariableChangeListener(IVariableChangeListener l) {
		if (!variableChangeListeners.contains(l)) {
			variableChangeListeners.add(l);
		}
	}
	
	public List<IVariableChangeListener> getVariableChangeListeners() {
		return variableChangeListeners;
	}
	
	/**
	 * retrieves the variable class type of this variable.
	 * @see DefaultVariable
	 * @see FieldVariable
	 * @see MethodVariable
	 * @return the variable class type
	 */
	public abstract Class<? extends AVariable> getVariableType();
	
	public Object getData(Object... args) {
		return getData();
	}

	/**
	 * retrieves the namespace or scope of this variable. This
	 * could be one of: system, user
	 * @see #getNamespace(String)
	 * @return the namespace
	 */
	public String getNameSpace() {
		return getNamespace(this.getIdentifier());
	}
	
	/**
	 * retrieves the identifier without the initial namespace.
	 * @return the identifier without namespace
	 */
	public String getIdentifierWithoutNamespace() {
		return getIdentifierWithoutNamespace(this.getIdentifier());
	}
	
	/**
	 * retrieves the namespace or scope of this variable. This could
	 * be one of: system, user. This method is a generic static
	 * version used within {@link #getNameSpace()}.
	 * @param id the id to check
	 * @return the namespace of the parameter id
	 */
	public static String getNamespace( String id ) {
		String ns = "";
		int occ = id.indexOf('.');
		if (occ >= 0) {
			ns = id.substring(0, occ);
		}
		return ns;
	}
	
	/**
	 * retrieves the identifier without the initial namespace.
	 *  This method is a generic static
	 * version used within {@link #getIdentifierWithoutNamespace()}.
	 * @param id the identifier to trim the namespace from
	 * @return the identifier without namespace
	 */
	public static String getIdentifierWithoutNamespace( String id ) {
		return id.replaceFirst(getNamespace(id) + ".", "");
	}
}
