/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.ide.eclipse.script.information;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import saker.build.ide.eclipse.extension.script.information.IScriptInformationRoot;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.SimpleTextPartition;
import saker.build.scripting.model.TextPartition;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class BuildScriptScriptInformationRoot implements IScriptInformationRoot {
	private final String schemaIdentifier;
	private final Map<String, String> schemaMetaData;
	private final List<BuildScriptScriptInformationEntry> entries = new ArrayList<>();

	public BuildScriptScriptInformationRoot(ScriptTokenInformation tokeninfo) {
		this.schemaIdentifier = tokeninfo.getSchemaIdentifier();
		this.schemaMetaData = ImmutableUtils.makeImmutableNavigableMap(tokeninfo.getSchemaMetaData());
		PartitionedTextContent description = tokeninfo.getDescription();
		if (description != null) {
			Iterable<? extends TextPartition> partitions = description.getPartitions();
			if (partitions != null) {
				for (TextPartition partition : partitions) {
					if (partition == null) {
						continue;
					}
					entries.add(new BuildScriptScriptInformationEntry(partition));
				}
			}
		}
	}

	@Override
	public List<BuildScriptScriptInformationEntry> getEntries() {
		return entries;
	}

	@Override
	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	public static List<TextPartition> getFilteredTextPartitions(PartitionedTextContent partitioned) {
		if (partitioned == null) {
			return null;
		}

		//create a set first to remove duplicates
		List<TextPartition> partitions = ObjectUtils
				.newArrayList(ObjectUtils.newLinkedHashSet(partitioned.getPartitions()));

		List<TextPartition> contentonlies = new ArrayList<>();
		List<TextPartition> titleonlies = new ArrayList<>();
		//remove empties, promote titles, move content onlies to the back
		for (ListIterator<TextPartition> it = partitions.listIterator(); it.hasNext();) {
			TextPartition partition = it.next();
			if (partition == null) {
				it.remove();
				continue;
			}
			String title = ObjectUtils.nullDefault(partition.getTitle(), "");
			String subtitle = ObjectUtils.nullDefault(partition.getSubTitle(), "");
			FormattedTextContent content = partition.getContent();
			if (title.isEmpty()) {
				//promote title
				partition = new SimpleTextPartition(subtitle, null, content);
				it.set(partition);
				title = subtitle;
				subtitle = "";

				if (title.isEmpty()) {
					//no title, only content.
					if (isEmptyFormattedContent(content)) {
						//all empty
						it.remove();
						continue;
					}
					contentonlies.add(partition);
					it.remove();
					continue;
				}
			} else {
				if (subtitle.isEmpty()) {
					if (isEmptyFormattedContent(content)) {
						titleonlies.add(partition);
						it.remove();
						continue;
					}
				}
			}
		}

		for (int i = 0; i < partitions.size(); i++) {
			TextPartition partition = partitions.get(i);
			if (!isEmptyFormattedContent(partition.getContent())) {
				continue;
			}
			//we have empty content
			int sametitled = indexOfWithSameTitle(partitions, partition);
			if (sametitled < 0) {
				continue;
			}
			if (sametitled < i) {
				//the partition with the same title is before us
				partitions.remove(i);
				--i;
			} else {
				//the partition with the same title is after us
				TextPartition sametitledpartition = partitions.get(sametitled);
				if (isEmptyFormattedContent(sametitledpartition.getContent())) {
					//the entry with the same title is empty
					//remove it as we already have that titles
					partitions.remove(sametitled);
				} else {
					//we are empty, and the same titled is not
					//move the same titled to the position as us
					partitions.set(i, sametitledpartition);
					partitions.remove(sametitled);
				}
			}
		}
		for (int i = 0; i < titleonlies.size(); i++) {
			TextPartition partition = titleonlies.get(i);
			int containedtitled = indexOfWithContainedTitle(partitions, partition, partition.getTitle());
			if (containedtitled < 0) {
				partitions.add(partition);
			} else {
				//don't add, as a partition with the same title or subtitle is already present.
				continue;
			}
		}
		partitions.addAll(contentonlies);

		return partitions;
	}

	private static int indexOfWithContainedTitle(List<TextPartition> partitions, TextPartition partition,
			String title) {
		int i = 0;
		for (TextPartition p : partitions) {
			if (p != partition) {
				//dont consider self
				if (title.equals(ObjectUtils.nullDefault(p.getTitle(), ""))
						|| title.equals(ObjectUtils.nullDefault(p.getSubTitle(), ""))) {
					return i;
				}
			}
			++i;
		}
		return -1;
	}

	private static int indexOfWithSameTitle(List<TextPartition> partitions, TextPartition partition) {
		int i = 0;
		String title = ObjectUtils.nullDefault(partition.getTitle(), "");
		String subtitle = ObjectUtils.nullDefault(partition.getSubTitle(), "");
		for (TextPartition p : partitions) {
			if (p != partition) {
				//dont consider self
				if (title.equals(ObjectUtils.nullDefault(p.getTitle(), ""))
						&& subtitle.equals(ObjectUtils.nullDefault(p.getSubTitle(), ""))) {
					return i;
				}
			}
			++i;
		}
		return -1;
	}

	private static boolean isEmptyFormattedContent(FormattedTextContent text) {
		if (text == null) {
			return true;
		}
		Set<String> formats = text.getAvailableFormats();
		if (ObjectUtils.isNullOrEmpty(formats)) {
			return true;
		}
		for (String f : formats) {
			if (!ObjectUtils.isNullOrEmpty(text.getFormattedText(f))) {
				return false;
			}
		}
		return true;
	}

	public static List<BuildScriptScriptInformationEntry> getFilteredBuildScriptScriptInformationEntries(
			List<? extends BuildScriptScriptInformationEntry> entries) {
		if (entries == null) {
			return null;
		}

		//create a set first to remove duplicates
		List<BuildScriptScriptInformationEntry> partitions = ObjectUtils
				.newArrayList(ObjectUtils.newLinkedHashSet(entries));

		List<BuildScriptScriptInformationEntry> contentonlies = new ArrayList<>();
		List<BuildScriptScriptInformationEntry> titleonlies = new ArrayList<>();
		//remove empties, promote titles, move content onlies to the back
		for (ListIterator<BuildScriptScriptInformationEntry> it = partitions.listIterator(); it.hasNext();) {
			BuildScriptScriptInformationEntry partition = it.next();
			if (partition == null) {
				it.remove();
				continue;
			}
			String title = ObjectUtils.nullDefault(partition.getTitle(), "");
			String subtitle = ObjectUtils.nullDefault(partition.getSubTitle(), "");
			FormattedTextContent content = partition.getContent();
			if (title.isEmpty()) {
				//promote title
				BuildScriptScriptInformationEntry promotedentry = new BuildScriptScriptInformationEntry(subtitle, null,
						content);
				promotedentry.setIconSource(partition.getIconSource());
				partition = promotedentry;
				it.set(partition);
				title = subtitle;
				subtitle = "";

				if (title.isEmpty()) {
					//no title, only content.
					if (isEmptyFormattedContent(content)) {
						//all empty
						it.remove();
						continue;
					}
					contentonlies.add(partition);
					it.remove();
					continue;
				}
			} else {
				if (subtitle.isEmpty()) {
					if (isEmptyFormattedContent(content)) {
						titleonlies.add(partition);
						it.remove();
						continue;
					}
				}
			}
		}

		for (int i = 0; i < partitions.size(); i++) {
			BuildScriptScriptInformationEntry partition = partitions.get(i);
			if (!isEmptyFormattedContent(partition.getContent())) {
				continue;
			}
			//we have empty content
			int sametitled = indexOfWithSameTitleBuildScriptScriptInformationEntry(partitions, partition);
			if (sametitled < 0) {
				continue;
			}
			if (sametitled < i) {
				//the partition with the same title is before us
				partitions.remove(i);
				--i;
			} else {
				//the partition with the same title is after us
				BuildScriptScriptInformationEntry sametitledpartition = partitions.get(sametitled);
				if (isEmptyFormattedContent(sametitledpartition.getContent())) {
					//the entry with the same title is empty
					//remove it as we already have that titles
					partitions.remove(sametitled);
				} else {
					//we are empty, and the same titled is not
					//move the same titled to the position as us
					partitions.set(i, sametitledpartition);
					partitions.remove(sametitled);
				}
			}
		}
		for (int i = 0; i < titleonlies.size(); i++) {
			BuildScriptScriptInformationEntry partition = titleonlies.get(i);
			int containedtitled = indexOfWithContainedTitleBuildScriptScriptInformationEntry(partitions, partition,
					partition.getTitle());
			if (containedtitled < 0) {
				partitions.add(partition);
			} else {
				//don't add, as a partition with the same title or subtitle is already present.
				continue;
			}
		}
		partitions.addAll(contentonlies);

		return partitions;
	}

	private static int indexOfWithContainedTitleBuildScriptScriptInformationEntry(
			List<BuildScriptScriptInformationEntry> partitions, BuildScriptScriptInformationEntry partition,
			String title) {
		int i = 0;
		for (BuildScriptScriptInformationEntry p : partitions) {
			if (p != partition) {
				//dont consider self
				if (title.equals(ObjectUtils.nullDefault(p.getTitle(), ""))
						|| title.equals(ObjectUtils.nullDefault(p.getSubTitle(), ""))) {
					return i;
				}
			}
			++i;
		}
		return -1;
	}

	private static int indexOfWithSameTitleBuildScriptScriptInformationEntry(
			List<BuildScriptScriptInformationEntry> partitions, BuildScriptScriptInformationEntry partition) {
		int i = 0;
		String title = ObjectUtils.nullDefault(partition.getTitle(), "");
		String subtitle = ObjectUtils.nullDefault(partition.getSubTitle(), "");
		for (BuildScriptScriptInformationEntry p : partitions) {
			if (p != partition) {
				//dont consider self
				if (title.equals(ObjectUtils.nullDefault(p.getTitle(), ""))
						&& subtitle.equals(ObjectUtils.nullDefault(p.getSubTitle(), ""))) {
					return i;
				}
			}
			++i;
		}
		return -1;
	}
}
