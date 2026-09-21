/*
 Copyright (c) 2026 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.autocontentprovider.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.db.CascadeInfo;
import pl.selvin.android.autocontentprovider.db.ColumnInfo;
import pl.selvin.android.autocontentprovider.db.ColumnInfoFactory;
import pl.selvin.android.autocontentprovider.db.ColumnType;
import pl.selvin.android.autocontentprovider.db.TableInfo;

/**
 * Covers {@link TableInfo}'s schema generation and the validation in its constructor.
 * Pure string/metadata assertions - no database is opened.
 */
@RunWith(AndroidJUnit4.class)
public class TableInfoTest {

	private static final ColumnInfoFactory COLUMN_INFO_FACTORY = (column, field) -> {
		final String columnName = (String) field.get(null);
		if (columnName == null)
			throw new IllegalStateException("Column name can not be null!");
		return new ColumnInfo(columnName, column);
	};

	private static TableInfo tableInfo(String tableName) {
		final TableInfo tableInfo = TestProvider.CONTENT_HELPER.getTableFromName(tableName);
		assertNotNull("no TableInfo for " + tableName, tableInfo);
		return tableInfo;
	}

	private static TableInfo buildTableInfo(Class<?> tableClass) throws Exception {
		return new TableInfo(tableClass.getAnnotation(Table.class), tableClass,
				TestProvider.AUTHORITY, COLUMN_INFO_FACTORY);
	}

	private static ColumnInfo column(TableInfo tableInfo, String name) {
		for (final ColumnInfo columnInfo : tableInfo.columns) {
			if (columnInfo.name.equals(name))
				return columnInfo;
		}
		throw new AssertionError("no column " + name + " on " + tableInfo.name);
	}

	private static List<String> columnNames(List<ColumnInfo> columns) {
		final ArrayList<String> names = new ArrayList<>(columns.size());
		for (final ColumnInfo columnInfo : columns)
			names.add(columnInfo.name);
		return names;
	}

	// ---- createStatement ----------------------------------------------------------------

	/**
	 * Whole generated statement for the simplest table. Deliberately exact, including the
	 * double space left where an empty extras lands: this is the string handed to SQLite,
	 * so any accidental change to it should be seen and approved rather than silently shipped.
	 */
	@Test
	public void createStatementIsStable() {
		assertEquals("CREATE TABLE IF NOT EXISTS Status ([ID] INTEGER  NOT NULL , " +
						"[Name] VARCHAR COLLATE NOCASE NOT NULL , PRIMARY KEY (ID));",
				tableInfo(DatabaseTest.Status.TABLE_NAME).createStatement());
	}

	@Test
	public void createStatementUsesTypeNameOfEveryColumnType() {
		final String create = tableInfo(DatabaseTest.AllTypes.TABLE_NAME).createStatement();
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.ID + "] INTEGER"));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.NAME + "] VARCHAR"));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.GUID + "] GUID"));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.DATE + "] DATETIME"));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.NUM + "] NUMERIC"));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.FLAG + "] BOOLEAN"));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.DATA + "] BLOB"));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.AMOUNT + "] DECIMAL"));
	}

	@Test
	public void createStatementMarksOnlyNonNullableColumnsNotNull() {
		final String create = tableInfo(DatabaseTest.AllTypes.TABLE_NAME).createStatement();
		// Flag is not nullable, Data is
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.FLAG + "] BOOLEAN  NOT NULL "));
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.DATA + "] BLOB "));
		assertTrue(create, !create.contains("[" + DatabaseTest.AllTypes.DATA + "] BLOB  NOT NULL "));
	}

	@Test
	public void createStatementIncludesExtras() {
		final String create = tableInfo(DatabaseTest.AllTypes.TABLE_NAME).createStatement();
		assertTrue(create, create.contains("[" + DatabaseTest.AllTypes.NAME + "] VARCHAR "
				+ Column.COLLATE_NO_CASE));
	}

	@Test
	public void createStatementKeepsCompositePrimaryKeyOrder() {
		final String create = tableInfo(DatabaseTest.Composite.TABLE_NAME).createStatement();
		assertTrue(create, create.endsWith(", PRIMARY KEY (" + DatabaseTest.Composite.GROUP_ID
				+ ", " + DatabaseTest.Composite.CODE + ", " + DatabaseTest.Composite.SEQ + "));"));
	}

	/** Computed columns are projection-only and must never reach the CREATE TABLE. */
	@Test
	public void createStatementOmitsComputedColumns() {
		final TableInfo allTypes = tableInfo(DatabaseTest.AllTypes.TABLE_NAME);
		assertEquals(List.of(DatabaseTest.AllTypes.LABEL), columnNames(allTypes.computedColumns));
		assertTrue(!allTypes.createStatement().contains(DatabaseTest.AllTypes.LABEL));
	}

	@Test
	public void dropStatement() {
		assertEquals("DROP TABLE IF EXISTS " + DatabaseTest.Status.TABLE_NAME,
				tableInfo(DatabaseTest.Status.TABLE_NAME).dropStatement());
	}

	// ---- getSelection -------------------------------------------------------------------

	@Test
	public void getSelectionSingleKey() {
		assertEquals(DatabaseTest.Status.ID + "=?",
				tableInfo(DatabaseTest.Status.TABLE_NAME).getSelection());
	}

	@Test
	public void getSelectionCompositeKey() {
		assertEquals(DatabaseTest.Composite.GROUP_ID + "=? AND " + DatabaseTest.Composite.CODE
						+ "=? AND " + DatabaseTest.Composite.SEQ + "=?",
				tableInfo(DatabaseTest.Composite.TABLE_NAME).getSelection());
	}

	/** Cached in a field after the first call - second call must not append to it again. */
	@Test
	public void getSelectionIsStableAcrossCalls() {
		final TableInfo composite = tableInfo(DatabaseTest.Composite.TABLE_NAME);
		assertEquals(composite.getSelection(), composite.getSelection());
	}

	// ---- projection map -----------------------------------------------------------------

	@Test
	public void projectionMapAliasesRowIdAsUnderscoreId() {
		assertEquals("[" + DatabaseTest.Status.TABLE_NAME + "].ROWID AS _id",
				tableInfo(DatabaseTest.Status.TABLE_NAME).map.get("_id"));
	}

	@Test
	public void projectionMapUsesRowIdAliasWhenSet() {
		assertEquals("[" + DatabaseTest.Composite.TABLE_NAME + "]." + DatabaseTest.Composite.GROUP_ID
						+ " AS _id",
				tableInfo(DatabaseTest.Composite.TABLE_NAME).map.get("_id"));
	}

	@Test
	public void projectionMapMapsPlainColumnsToThemselves() {
		assertEquals(DatabaseTest.Status.NAME,
				tableInfo(DatabaseTest.Status.TABLE_NAME).map.get(DatabaseTest.Status.NAME));
	}

	@Test
	public void projectionMapAliasesComputedColumns() {
		assertEquals(DatabaseTest.AllTypes.NAME + " || '-' || " + DatabaseTest.AllTypes.GUID
						+ " AS " + DatabaseTest.AllTypes.LABEL,
				tableInfo(DatabaseTest.AllTypes.TABLE_NAME).map.get(DatabaseTest.AllTypes.LABEL));
	}

	// ---- parsed metadata ----------------------------------------------------------------

	@Test
	public void mimeTypes() {
		final TableInfo status = tableInfo(DatabaseTest.Status.TABLE_NAME);
		assertEquals("vnd.android.cursor.dir/" + TestProvider.AUTHORITY + "."
				+ DatabaseTest.Status.TABLE_NAME, status.dirMime);
		assertEquals("vnd.android.cursor.item/" + TestProvider.AUTHORITY + "."
				+ DatabaseTest.Status.TABLE_NAME, status.itemMime);
	}

	@Test
	public void readonlyFlag() {
		assertTrue(!tableInfo(DatabaseTest.Status.TABLE_NAME).readonly);
		assertTrue(tableInfo(DatabaseTest.StatusReadonly.TABLE_NAME).readonly);
	}

	@Test
	public void notifyUris() {
		assertEquals(0, tableInfo(DatabaseTest.Status.TABLE_NAME).notifyUris.length);
		assertEquals(List.of(DatabaseTest.AllTypes.NOTIFY_URI),
				List.of(tableInfo(DatabaseTest.AllTypes.TABLE_NAME).notifyUris));
	}

	@Test
	public void cascadeInfoIsParsed() {
		final TableInfo parent = tableInfo(DatabaseTest.Parent.TABLE_NAME);
		assertEquals(2, parent.cascadeDelete.size());

		final CascadeInfo toChild = parent.cascadeDelete.get(0);
		assertEquals(DatabaseTest.Child.TABLE_NAME, toChild.table);
		assertEquals(List.of(DatabaseTest.Parent.ID, DatabaseTest.Parent.TENANT), List.of(toChild.pk));
		assertEquals(List.of(DatabaseTest.Child.PARENT_ID, DatabaseTest.Child.TENANT), List.of(toChild.fk));

		final CascadeInfo toNote = parent.cascadeDelete.get(1);
		assertEquals(DatabaseTest.Note.TABLE_NAME, toNote.table);
		assertEquals(List.of(DatabaseTest.Parent.CODE), List.of(toNote.pk));
		assertEquals(List.of(DatabaseTest.Note.PARENT_CODE), List.of(toNote.fk));

		// leaves of the chain
		assertEquals(0, tableInfo(DatabaseTest.GrandChild.TABLE_NAME).cascadeDelete.size());
		assertEquals(0, tableInfo(DatabaseTest.Note.TABLE_NAME).cascadeDelete.size());
	}

	@Test
	public void columnFlagsAreParsed() {
		final TableInfo allTypes = tableInfo(DatabaseTest.AllTypes.TABLE_NAME);
		final ColumnInfo data = column(allTypes, DatabaseTest.AllTypes.DATA);
		assertEquals(ColumnType.BLOB, data.type);
		assertTrue(data.nullable);
		assertEquals(Column.EMPTY, data.extras);

		final ColumnInfo name = column(allTypes, DatabaseTest.AllTypes.NAME);
		assertEquals(ColumnType.VARCHAR, name.type);
		assertTrue(!name.nullable);
		assertEquals(Column.COLLATE_NO_CASE, name.extras);
	}

	// ---- constructor validation ---------------------------------------------------------

	@Test
	public void tableWithoutColumnsIsRejected() {
		final RuntimeException ex = assertThrows(RuntimeException.class,
				() -> buildTableInfo(InvalidTables.NoColumns.class));
		assertEquals("Table has no columns", ex.getMessage());
	}

	@Test
	public void tableWithoutTableNameIsRejected() {
		final RuntimeException ex = assertThrows(RuntimeException.class,
				() -> buildTableInfo(InvalidTables.NoTableName.class));
		assertEquals("There is no field with @TableName annotation", ex.getMessage());
	}

	@Test
	public void tableWithTwoTableNamesIsRejected() {
		assertThrows(RuntimeException.class,
				() -> buildTableInfo(InvalidTables.TwoTableNames.class));
	}

	/**
	 * A primaryKeys entry that is not a column of the table. Characterises current
	 * behaviour: construction fails (List.copyOf rejects the null lookup result) rather
	 * than producing a TableInfo with a null primary key.
	 */
	@Test
	public void primaryKeyNamingUnknownColumn() {
		assertThrows(Exception.class, () -> buildTableInfo(InvalidTables.UnknownPrimaryKey.class));
	}

	// ---- ColumnType ---------------------------------------------------------------------

	@Test
	public void columnTypeNames() {
		assertEquals("INTEGER", ColumnType.getName(ColumnType.INTEGER));
		assertEquals("VARCHAR", ColumnType.getName(ColumnType.VARCHAR));
		assertEquals("GUID", ColumnType.getName(ColumnType.GUID));
		assertEquals("DATETIME", ColumnType.getName(ColumnType.DATETIME));
		assertEquals("NUMERIC", ColumnType.getName(ColumnType.NUMERIC));
		assertEquals("BOOLEAN", ColumnType.getName(ColumnType.BOOLEAN));
		assertEquals("BLOB", ColumnType.getName(ColumnType.BLOB));
		assertEquals("DECIMAL", ColumnType.getName(ColumnType.DECIMAL));
	}
}
