/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.decisiontable.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.drools.decisiontable.parser.xls.PropertiesSheetListener;
import org.drools.decisiontable.parser.xls.PropertiesSheetListener.CaseInsensitiveMap;
import org.drools.template.model.Condition;
import org.drools.template.model.Consequence;
import org.drools.template.model.Global;
import org.drools.template.model.Import;
import org.drools.template.model.Package;
import org.drools.template.model.Rule;
import org.drools.template.parser.DecisionTableParseException;

import static org.drools.decisiontable.parser.ActionType.Code;
import static org.drools.template.model.Rule.MAX_ROWS;
import static org.drools.util.StringUtils.replaceOutOfQuotes;

/**
 * An object of this class is prepared to receive calls passing it the
 * contents of a spreadsheet containing one or more decision tables.
 * Each of these tables is then expanded into a set of similar rules,
 * varying to a degree with respect to the patterns and actions.
 * 
 * A "rule set" starts with some overall definitions such as imports,
 * globals, functions and queries.
 * 
 * A table is identifed by a cell beginning with the text "RuleTable". The first
 * row after the table identifier defines the column type: either a pattern of
 * the condition or an action for the consequence, or an attribute.
 * 
 * The second row contains optional pattern type declarations. If cells in
 * this row are merged, then all snippets below the merged stretch become part of
 * the same pattern, as separate constraints.
 *
 * The third row identifies the java code block associated with the condition
 * or consequence. This code block should include one or more parameter markers
 * for the insertion of values defined in cells of that column.
 * 
 * The third row is available for comments on the purpose of the column.
 *
 * All subsequent rows identify rules with the set, providing values to be
 * inserted where there are markers in the code snippets defined in the third
 * row, or for the attribute identified by the column header.
 * 
 *         href="mailto:michael.neale@gmail.com"> Michael Neale </a>
 */
public class DefaultRuleSheetListener
implements RuleSheetListener {

    //keywords
    public static final String            QUERIES_TAG            = "Queries";
    public static final String            FUNCTIONS_TAG          = "Functions";
    public static final String            DECLARES_TAG           = "Declare";
    public static final String            UNIT_TAG               = "Unit";
    public static final String            IMPORT_TAG             = "Import";
    public static final String            SEQUENTIAL_FLAG        = "Sequential";
    public static final String            ESCAPE_QUOTES_FLAG     = "EscapeQuotes";
    public static final String            MIN_SALIENCE_TAG       = "SequentialMinPriority";
    public static final String            MAX_SALIENCE_TAG       = "SequentialMaxPriority";
    public static final String            NUMERIC_DISABLED_FLAG  = "NumericDisabled";
    public static final String            IGNORE_NUMERIC_FORMAT_FLAG     = "IgnoreNumericFormat";
    public static final String            VARIABLES_TAG          = "Variables";
    public static final String            RULE_TABLE_TAG         = "ruletable";
    public static final String            RULESET_TAG            = "RuleSet";
    public static final String            DIALECT_TAG            = "Dialect";
    private static final int              ACTION_ROW             = 1;
    private static final int              OBJECT_TYPE_ROW        = 2;
    private static final int              CODE_ROW               = 3;
    private static final int              LABEL_ROW              = 4;

    //state machine variables for this parser
    private boolean                       _isInRuleTable           = false;
    private int                           _ruleRow;
    private int                           _ruleStartColumn;
    private int                           _ruleStartRow;
    private Rule                          _currentRule;
    private String                        _currentRulePrefix;
    private boolean                       _currentSequentialFlag   = false;                       // indicates that we are in sequential mode
    private boolean                       _currentEscapeQuotesFlag = true;                        // indicates that we are escaping quotes
    private boolean                       _currentNumericDisabledFlag = false;                    // indicates that we use String instead of double
    private boolean                       _currentIgnoreNumericFormatFlag = false;                 // indicates that we don't use formatter for numeric value (except "General")
    private int                           _currentSalience = MAX_ROWS;                            // set to the start value of the salience and decremented for each row
    private int                           _minSalienceTag = 0;                                    // used to check if this minimum salience value is not violated

    //accumulated output
    private Map<Integer, ActionType>       _actions;
    private final HashMap<Integer, String> _cellComments          = new HashMap<>();
    private final List<Rule>               _ruleList              = new ArrayList<>();

    //need to keep an ordered list of this to make conditions appear in the right order
    private Collection<SourceBuilder>     sourceBuilders;

    private final PropertiesSheetListener _propertiesListener     = new PropertiesSheetListener();

    private final boolean showPackage;
    private final boolean trimCell;
    private String worksheetName = null;

    /**
     * Constructor.
     */
    public DefaultRuleSheetListener() {
        this( true, true );
    }

    /**
     * Constructor.
     * @param showPackage if true, the rule set name is passed to the resulting package
     */
    public DefaultRuleSheetListener(boolean showPackage, boolean trimCell) {
        this.showPackage = showPackage;
        this.trimCell = trimCell;
    }

    public void setWorksheetName(String worksheetName) {
        this.worksheetName = worksheetName;
    }

    /* (non-Javadoc)
    * @see org.kie.decisiontable.parser.RuleSheetListener#getProperties()
    */
    public CaseInsensitiveMap getProperties() {
        return this._propertiesListener.getProperties();
    }

    /* (non-Javadoc)
     * @see org.kie.decisiontable.parser.RuleSheetListener#getRuleSet()
     */
    public Package getRuleSet() {
        if ( this._ruleList.isEmpty() ) {
            throw new DecisionTableParseException( "No RuleTable cells in spreadsheet." );
        }
        return buildRuleSet();
    }

    /**
     * Add a new rule to the current list of rules
     * @param newRule
     */
    protected void addRule(final Rule newRule) {
        this._ruleList.add( newRule );
    }

    private Package buildRuleSet() {
        final String defaultPackageName = "rule_table";
        final String rulesetName =
            getProperties().getSingleProperty( RULESET_TAG, defaultPackageName );

        final Package ruleset = new Package( (showPackage) ? rulesetName : null );
        for ( Rule rule : this._ruleList ) {
            ruleset.addRule( rule );
        }

        List<String> units = getProperties().getProperty( UNIT_TAG );
        if (units != null && !units.isEmpty()) {
            ruleset.setRuleUnit( units.get( 0 ) );
        }

        List<String> dialects = getProperties().getProperty( DIALECT_TAG );
        if (dialects != null && !dialects.isEmpty()) {
            ruleset.setDialect( dialects.get( 0 ) );
        }

        final List<Import> importList = RuleSheetParserUtil.getImportList( getProperties().getProperty( IMPORT_TAG ) );
        for ( Import import1 : importList ) {
            ruleset.addImport( import1 );
        }

        final List<Global> variableList = RuleSheetParserUtil.getVariableList( getProperties().getProperty( VARIABLES_TAG ) );
        for ( Global global : variableList ) {
            ruleset.addVariable( global );
        }

        final List<String> functions = getProperties().getProperty( FUNCTIONS_TAG );
        if( functions != null ){
            for( String function: functions ){
                ruleset.addFunctions( function );
            }
        }

        final List<String> queries = getProperties().getProperty( QUERIES_TAG );
        if( queries != null ){
            for( String query: queries ){
                ruleset.addQueries( query );
            }
        }

        final List<String> declarations = getProperties().getProperty( DECLARES_TAG );
        if( declarations != null ){
            for( String declaration: declarations ){
                ruleset.addDeclaredType( declaration );
            }
        }
        
        for( Code code: ActionType.ATTRIBUTE_CODE_SET ){
            List<String> values = getProperties().getProperty( code.getColHeader() );
            if( values != null ){
                if( values.size() > 1 ){
                    List<String> cells = getProperties().getPropertyCells( code.getColHeader() );
                    throw new DecisionTableParseException( "Multiple values for " + code.getColHeader() +
                            " in cells " + cells.toString() );
                }
                String value = values.get( 0 );
                switch( code ){
                case SALIENCE:
                    try {
                        ruleset.setSalience( Integer.valueOf( value ) );
                    } catch( NumberFormatException nfe ){
                        throw new DecisionTableParseException( "Priority is not an integer literal, in cell " +
                                getProperties().getSinglePropertyCell( code.getColHeader() ) );
                    }
                    break;
                case DURATION:
                    try {
                        ruleset.setDuration( Long.valueOf( value ) );
                    } catch( NumberFormatException nfe ){
                        throw new DecisionTableParseException( "Duration is not an integer literal, in cell " +
                                getProperties().getSinglePropertyCell( code.getColHeader() )  );
                    }
                    break;
                case TIMER:
                    ruleset.setTimer( value );
                    break;
                case ENABLED:
                    ruleset.setEnabled( RuleSheetParserUtil.isStringMeaningTrue( value ) );
                    break;
                case CALENDARS:
                    ruleset.setCalendars( value );
                    break;
                case NOLOOP:
                    ruleset.setNoLoop( RuleSheetParserUtil.isStringMeaningTrue( value ) );
                    break;
                case LOCKONACTIVE:
                    ruleset.setLockOnActive( RuleSheetParserUtil.isStringMeaningTrue( value ) );
                    break;
                case AUTOFOCUS:
                    ruleset.setAutoFocus( RuleSheetParserUtil.isStringMeaningTrue( value ) );
                    break;
                case ACTIVATIONGROUP:
                    ruleset.setActivationGroup( value );
                    break;
                case AGENDAGROUP:
                    ruleset.setAgendaGroup( value );
                    break;
                case RULEFLOWGROUP:
                    ruleset.setRuleFlowGroup( value );
                    break;
                case DATEEFFECTIVE:
                    ruleset.setDateEffective( value );
                    break;
                case DATEEXPIRES:
                    ruleset.setDateExpires( value );
                    break;
                }
            }
        }

        return ruleset;
    }

    /*
     * (non-Javadoc)
     *
     * @see my.hssf.util.SheetListener#startSheet(java.lang.String)
     */
    public void startSheet(final String name) {
        // nothing to see here... move along..
    }

    /*
     * (non-Javadoc)
     *
     * @see my.hssf.util.SheetListener#finishSheet()
     */
    public void finishSheet() {
        this._propertiesListener.finishSheet();
        finishRuleTable();
        flushRule();
    }

    /*
     * (non-Javadoc)
     *
     * @see my.hssf.util.SheetListener#newRow()
     */
    public void newRow(final int rowNumber,
            final int columns) {
        if ( _currentRule != null ) {
            flushRule();
        }
        // nothing to see here... these aren't the droids your looking for..
        // move along...
    }

    /**
     * This makes sure that the rules have all their components added.
     * As when there are merged/spanned cells, they may be left out.
     */
    private void flushRule() {
        if ( sourceBuilders == null ) {
            return;
        }
        for ( SourceBuilder src : sourceBuilders ) {
            if ( src.hasValues() ) {
                switch ( src.getActionTypeCode() ) {
                    case CONDITION:
                        Condition cond = new Condition();
                        cond.setSnippet( replaceOutOfQuotes( src.getResult(), "\\n", " " ) );
                        _currentRule.addCondition( cond );
                        break;
                    case ACTION:
                        Consequence cons = new Consequence();
                        cons.setSnippet( replaceOutOfQuotes( src.getResult(), "\\n", " " ) );
                        _currentRule.addConsequence( cons );
                        break;
                    case METADATA:
                        _currentRule.addMetadata( src.getResult() );
                        break;
                }
                src.clearValues();
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see my.hssf.util.SheetListener#newCell(int, int, java.lang.String)
     */
    public void newCell(final int row,
            final int column,
            final String value,
            int mergedColStart) {
        if ( isCellValueEmpty( value ) ) {
            return;
        }
        if ( _isInRuleTable && row == this._ruleStartRow ) {
            return;
        }
        if ( this._isInRuleTable ) {
            processRuleCell( row, column, value, mergedColStart );
        } else {
            processNonRuleCell( row, column, value );
        }
    }

    /**
     * This gets called each time a "new" rule table is found.
     */
    private void initRuleTable(final int row,
                               final int column,
                               final String value,
                               boolean firstTable) {
        preInitRuleTable( row, column, value );
        this._isInRuleTable = true;
        this._actions = new HashMap<>();
        this.sourceBuilders = new TreeSet<>(Comparator.comparing( SourceBuilder::getColumn ));
        this._ruleStartColumn = column;
        this._ruleStartRow = row;
        this._ruleRow = row + LABEL_ROW + 1;

        // setup stuff for the rules to come.. (the order of these steps are
        // important !)
        this._currentRulePrefix = RuleSheetParserUtil.getRuleName( value );
        if (this.worksheetName != null) {
            this._currentRulePrefix += " " + worksheetName;
        }

        this._currentSequentialFlag = getFlagValue(SEQUENTIAL_FLAG, false);
        this._currentEscapeQuotesFlag = getFlagValue(ESCAPE_QUOTES_FLAG, true);
        this._currentNumericDisabledFlag = getFlagValue(NUMERIC_DISABLED_FLAG, false);
        this._currentIgnoreNumericFormatFlag = getFlagValue(IGNORE_NUMERIC_FORMAT_FLAG, false);

        if (firstTable) {
            this._currentSalience = getNumericValue( MAX_SALIENCE_TAG, this._currentSalience );
            this._minSalienceTag = getNumericValue( MIN_SALIENCE_TAG, this._minSalienceTag );
        }

        String headCell = RuleSheetParserUtil.rc2name( this._ruleStartRow, this._ruleStartColumn );
        String ruleCell = RuleSheetParserUtil.rc2name( this._ruleRow, this._ruleStartColumn );
        this._currentRule = createNewRuleForRow( this._ruleRow, headCell, ruleCell );

        this._ruleList.add( this._currentRule );
        postInitRuleTable( row, column, value );

    }

    /**
     * Called before rule table initialisation. Subclasses may
     * override this method to do additional processing.
     */
    protected void preInitRuleTable(int row,
            int column,
            String value) {
    }

    protected Rule getCurrentRule() {
        return _currentRule;
    }

    /**
     * Called after rule table initialisation. Subclasses may
     * override this method to do additional processing.
     */
    protected void postInitRuleTable(int row,
            int column,
            String value) {
    }

    private boolean getFlagValue(String name, boolean defaultValue) {
        return RuleSheetParserUtil.isStringMeaningTrue( getProperties().getSingleProperty( name, "" + defaultValue ) );
    }

    private int getNumericValue(String name, int defaultValue) {
        try {
            return Integer.parseInt( getProperties().getSingleProperty( name, "" + defaultValue ) );
        } catch (NumberFormatException nfe) {
            throw new DecisionTableParseException( "Invalid numeric value for option: " + name, nfe );
        }
    }

    private void finishRuleTable() {
        if ( this._isInRuleTable ) {
            this._currentSequentialFlag = false;
            this._isInRuleTable = false;

        }
    }

    private void processNonRuleCell(final int row,
            final int column,
            final String value) {
        String testVal = value.trim().toLowerCase();
        if (isRuleTable(testVal)) {
            initRuleTable( row, column, value.trim(), true );
        } else {
            this._propertiesListener.newCell( row, column, value, RuleSheetListener.NON_MERGED );
        }
    }

    private void processRuleCell(final int row,
            final int column,
            final String value,
            final int mergedColStart) {
        String trimVal = trimCell ? value.trim() : value;
        String testVal = trimVal.toLowerCase();
        if (isRuleTable(testVal)) {
            finishRuleTable();
            initRuleTable( row, column, trimVal, false );
            return;
        }

        // Ignore any comments cells preceding the first rule table column
        if ( column < this._ruleStartColumn ) {
            return;
        }

        // Ignore any further cells from the rule def row
        if ( row == this._ruleStartRow ) {
            return;
        }

        switch ( row - this._ruleStartRow ) {
        case ACTION_ROW :
            ActionType.addNewActionType( this._actions, trimVal, column, row );
            break;

        case OBJECT_TYPE_ROW :
            objectTypeRow( row, column, trimVal, mergedColStart );
            break;

        case CODE_ROW :
            codeRow( row, column, trimVal );
            break;

        case LABEL_ROW :
            labelRow( row, column, trimVal );
            break;

        default :
            nextDataCell( row, column, trimVal );
            break;
        }
    }

    private boolean isRuleTable(final String testVal) {
        return Objects.equals(RULE_TABLE_TAG, testVal) || testVal.startsWith(RULE_TABLE_TAG + " ");
    }

    /**
     * This is for handling a row where an object declaration may appear,
     * this is the row immediately above the snippets.
     * It may be blank, but there has to be a row here.
     *
     * Merged cells have "special meaning" which is why this is so freaking hard.
     * A future refactor may be to move away from an "event" based listener.
     */
    private void objectTypeRow(final int row,
            final int column,
            final String value,
            final int mergedColStart) {
        if ( value.contains( "$param" ) || value.contains( "$1" ) ) {
            throw new DecisionTableParseException( "It looks like you have snippets in the row that is " +
                    "meant for object declarations." + " Please insert an additional row before the snippets, " +
                    "at cell " + RuleSheetParserUtil.rc2name( row, column ) );
        }
        ActionType action = getActionForColumn( row, column );
        if ( mergedColStart == RuleSheetListener.NON_MERGED ) {
            if ( action.getCode() == Code.CONDITION ) {
                SourceBuilder src = new LhsBuilder( row-1, column, value );
                action.setSourceBuilder( src );
                this.sourceBuilders.add( src );

            } else if ( action.getCode() == Code.ACTION ) {
                SourceBuilder src = new RhsBuilder( Code.ACTION, row-1, column, value );
                action.setSourceBuilder( src );
                this.sourceBuilders.add( src );
            }
        } else {
            if ( column == mergedColStart ) {
                if ( action.getCode() == Code.CONDITION ) {
                    action.setSourceBuilder( new LhsBuilder( row-1, column, value ) );
                    this.sourceBuilders.add( action.getSourceBuilder() );
                } else if ( action.getCode() == Code.ACTION ) {
                    action.setSourceBuilder( new RhsBuilder( Code.ACTION, row-1, column, value ) );
                    this.sourceBuilders.add( action.getSourceBuilder() );
                }
            } else {
                ActionType startOfMergeAction = getActionForColumn( row, mergedColStart );
                action.setSourceBuilder( startOfMergeAction.getSourceBuilder() );
            }
        }
    }

    private void codeRow(final int row,
            final int column,
            final String value) {
        final ActionType actionType = getActionForColumn( row, column );
        if ( actionType.getSourceBuilder() == null ) {
            if ( actionType.getCode() == Code.CONDITION ) {
                actionType.setSourceBuilder( new LhsBuilder( row-2, column, null ) );
                this.sourceBuilders.add( actionType.getSourceBuilder() );
            } else if ( actionType.getCode() == Code.ACTION ) {
                actionType.setSourceBuilder( new RhsBuilder( Code.ACTION, row-2, column, null ) );
                this.sourceBuilders.add( actionType.getSourceBuilder() );
            } else if ( actionType.getCode() == Code.SALIENCE ) {
                actionType.setSourceBuilder( new LhsBuilder( row-2, column, null ) );
                this.sourceBuilders.add( actionType.getSourceBuilder() );
            } else if ( actionType.getCode() == Code.METADATA ) {
                actionType.setSourceBuilder( new RhsBuilder( Code.METADATA, row-2, column, null ) );
                this.sourceBuilders.add( actionType.getSourceBuilder() );
            }
        }

        if ( value.trim().equals( "" ) &&
            (actionType.getCode() == Code.ACTION ||
             actionType.getCode() == Code.CONDITION ||
             actionType.getCode() == Code.METADATA) ) {
            throw new DecisionTableParseException( "Code description in cell " +
                    RuleSheetParserUtil.rc2name( row, column ) +
            " does not contain any code specification. It should!" );
        }

        actionType.addTemplate( row, column, value );
    }

    private void labelRow(final int row,
            final int column,
            final String value) {
        final ActionType actionType = getActionForColumn( row, column );

        if ( ! value.trim().equals( "" ) && (actionType.getCode() == Code.ACTION ||
                actionType.getCode() == Code.CONDITION) ) {
            this._cellComments.put( column, value );
        } else {
            this._cellComments.put( column,
                    "From cell: " + RuleSheetParserUtil.rc2name( row, column ) );
        }
    }

    private ActionType getActionForColumn(final int row,
            final int column) {
        final ActionType actionType = this._actions.get( column );

        if ( actionType == null ) {
            throw new DecisionTableParseException( "Code description in cell " +
                    RuleSheetParserUtil.rc2name( row, column ) +
            " does not have an 'ACTION' or 'CONDITION' column header." );
        }

        return actionType;
    }

    private void nextDataCell(final int row,
            final int column,
            final String value) {
        final ActionType actionType = getActionForColumn( row, column );

        if ( row - this._ruleRow > 1 ) {
            // Encountered a row gap from the last rule.
            // This is not part of the ruleset.
            finishRuleTable();
            processNonRuleCell( row, column, value );
            return;
        }

        if ( row > this._ruleRow ) {
            // In a new row/rule
            String headCell = RuleSheetParserUtil.rc2name( this._ruleStartRow, this._ruleStartColumn );
            String ruleCell = RuleSheetParserUtil.rc2name( row, this._ruleStartColumn );
            this._currentRule = createNewRuleForRow( row, headCell, ruleCell );
            this._ruleList.add( this._currentRule );
            this._ruleRow++;
        }

        switch( actionType.getCode() ){
        case CONDITION:
        case ACTION:
        case METADATA:
            if (actionType.getSourceBuilder() == null) {
                throw new DecisionTableParseException( "Data cell " +
                        RuleSheetParserUtil.rc2name( row, column ) +
                        " has an empty column header." );
            }
            actionType.addCellValue( row, column, value, _currentEscapeQuotesFlag, trimCell );
            break;
        case SALIENCE:
            // Only if rule set is not sequential!
            if( ! this._currentSequentialFlag ){
                if( value.startsWith( "(" ) && value.endsWith( ")" ) ){
                    this._currentRule.setSalience( value );
                } else {
                    try {
                        this._currentRule.setSalience( Integer.valueOf( value ) );
                    } catch( NumberFormatException nfe ){
                        throw new DecisionTableParseException( "Priority is not an integer literal, in cell " +
                                RuleSheetParserUtil.rc2name( row, column ) );
                    }
                }
            }
            break;
        case NAME:
            this._currentRule.setName( value );
            break;
        case DESCRIPTION:
            this._currentRule.setDescription( value );
            break;
        case ACTIVATIONGROUP:
            this._currentRule.setActivationGroup( value );
            break;
        case AGENDAGROUP:
            this._currentRule.setAgendaGroup( value );
            break;
        case RULEFLOWGROUP:
            this._currentRule.setRuleFlowGroup( value );
            break;
        case NOLOOP:
            this._currentRule.setNoLoop( RuleSheetParserUtil.isStringMeaningTrue( value ) );
            break;
        case LOCKONACTIVE:
            this._currentRule.setLockOnActive( RuleSheetParserUtil.isStringMeaningTrue( value ) );
            break;
        case AUTOFOCUS:
            this._currentRule.setAutoFocus( RuleSheetParserUtil.isStringMeaningTrue( value ) );
            break;
        case DURATION:
            try {
                this._currentRule.setDuration( Long.valueOf( value ) );
            } catch( NumberFormatException nfe ){
                throw new DecisionTableParseException( "Duration is not an integer literal, in cell " +
                        RuleSheetParserUtil.rc2name( row, column ) );
            }
            break;
        case TIMER:
            this._currentRule.setTimer( value );
            break;
        case ENABLED:
            this._currentRule.setEnabled( RuleSheetParserUtil.isStringMeaningTrue( value ) );
            break;
        case CALENDARS:
            this._currentRule.setCalendars( value );
            break;
        case DATEEFFECTIVE:
            this._currentRule.setDateEffective( value );
            break;
        case DATEEXPIRES:
            this._currentRule.setDateExpires( value );
            break;
        }
    }

    private Rule createNewRuleForRow(final int row, final String headCell, final String ruleCell ) {
        Integer salience = null;
        if ( this._currentSequentialFlag ) {
            salience = _currentSalience--;
            if (salience < _minSalienceTag) {
                throw new DecisionTableParseException( "Salience less than the minimum specified on row: " + row );
            }
        }
        final int spreadsheetRow = row + 1;
        final String name = this._currentRulePrefix + "_" + spreadsheetRow;
        final Rule rule = new Rule( name, salience, spreadsheetRow );
        rule.setComment( " rule values at " + ruleCell + ", header at " + headCell );

        return rule;
    }

    private boolean isCellValueEmpty(final String value) {
        return value == null || "".equals( value.trim() );
    }

    public boolean isNumericDisabled() {
        return _currentNumericDisabledFlag;
    }

    public boolean doesIgnoreNumericFormat() {
        return _currentIgnoreNumericFormatFlag;
    }
}
