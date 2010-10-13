#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Template;

use 5.012;
use warnings;
use lib "..";

our $VERSION = v1;

use Log;
use DBI;
use Element;
use HTML::Entities;
use DlUtil;
use HTML::TreeBuilder;
use Digest::SHA;
use Data::Dumper;
use Time::HiRes qw(tv_interval gettimeofday);

my $l = Log->new();

#the somewhat shady container for all the collections data
sub clist {
	my ($pkg,$id) = @_;
	no strict 'refs';
	if (ref $pkg) {
		$id = $pkg->{id};
		$pkg = ref $pkg;	
	} 
	if (ref $id) {
		%$pkg = %$id; 
	}
	elsif (defined $id) {
		#return the reference to the collection
		return $$pkg{$id} ||= {};
	}
	else {
		return keys %$pkg;
	}
}

#saves the list
sub save_clist {
	my ($pkg) = @_;
	$pkg = ref($pkg) || $pkg;
	$l->trace("save clist ", $pkg);
	no strict 'refs';
	return UserPrefs::save_file($pkg,\%$pkg);
}

#initialises the core and loads collection list
sub init {
	my ($pkg) = @_;
	$l->trace('initialise ',$pkg);
	$l->warn('list already initialised, reinitialise') if $pkg->clist();
	return $pkg->_load_list();
}

#tries to load the collection list from file, creates it if it cant be found
sub _load_list {
	my ($pkg) = @_;
	$pkg->clist(UserPrefs::parse_file($pkg));
	if ($pkg->clist()) {
		$l->debug('loaded ' . scalar($pkg->clist()) . ' collections');
		return 1;
	}
	return $pkg->update_list();
}

#updates and saves the collection list
sub update_list {
	my ($pkg) = @_;
	my $list = $pkg->_create_list();
	$pkg->clist($list);
	$l->debug('found ' .  scalar($pkg->clist()) . ' collections');
	return $pkg->save_clist();
}

#$url -> $tree
#fetches the url and returns the tree
sub _get_tree {
	my ($s,$url) = @_;
	my $page = DlUtil::get($url);
	if (!$page->is_success()) {
		$l->error("error get: ", $url);
		return undef;
	}
	$l->trace('parse HTML into tree');
	my $tree = HTML::TreeBuilder->new();
	$tree->parse_content($page->decoded_content());
	return $tree;
}

#->%collection_hash
#returns a hash containing all the collection ids as keys and their names as values
sub list {
	my ($p) = @_;
	return map {$_ , $p->clist($_)->{name}} $p->clist();
}

#$query,@regex -> %list
sub search {
	my ($p,$filter,@re) = @_;
	$l->debug('search ', $p );
	my %cap;
	my @return;
	my $time = [gettimeofday];
	col: for my $id ($p->clist()) {
		my $l = $p->clist($id);
		my $sid = $id;
		$sid =~ s/^[^_]*+_//;
		reg: for my $re (@re) {
			if ($sid ~~ $re) {
				$cap{$id} = $1;
				next reg;
			}
			for my $k (@$filter ? @$filter : $p->_searchkeys()) {
				next unless defined $l->{$k};
				if ($l->{$k} ~~ $re) {
					$cap{$id} = $1;
					next reg;
				} 
			}
			#not all regexes matched, checking next collection
			next col;
		}
		#we have a matched
		push @return, [$id,$l->{name},$cap{$id}//$l->{name}]; #/ padre display bug
	}
	$l->trace('took ', tv_interval($time), ' seconds');
	return @return;
	# return map {[$_,$p->clist($_)->{name},$cap{$_}]} grep {
		# my $id = $_;
		# my $l = $p->clist($id);
		# $id =~ s/^[^_]*+_//;
		# @re == grep {
			# my $re = $_;
			# ($id ~~ $re and defined( $cap{$id} = $1 // '' )) or grep { #/ padre display bug
				# (defined $l->{$_} and $l->{$_} ~~ $re ) and defined( $cap{$id} = $1 // '' ); #/ padre display bug
			# } @$filter ? @$filter : $p->_searchkeys()
		# } @re;
	# } $p->clist();
}

#pkg, \%config -> \%config
#given a current config returns the configuration hash
sub config {
	my ($pkg,$cfg) = @_;
	return {};
}

#$class,$id -> $self
#creates a new core instance for a given collection
sub new {
	my ($class,$id) = @_;
	$l->trace("create new core $id");
	my $self = bless {id => $id}, $class;
	unless (keys %{$self->clist()}) {
		$l->error("unknown id ", $id);
		return undef;
	}
	return $self;
}

#-> @info
#returns a list of infos
sub about {
	my ($s) = @_;
	#all lowercased attributes are not intended for user
	return ['Name',$s->clist->{name}], map {[$_, $s->clist()->{$_}]} grep {$_ eq ucfirst $_} keys %{$s->clist()};
}

#$force
#fetches more information about the comic, force overwrites existing info
sub fetch_info {
	my ($s,$force) = @_;
	return undef if $s->clist()->{moreinfo} and !$force;
	$l->trace('fetching more info for ', $s->{id});
	$s->_fetch_info();
	$s->clist()->{moreinfo} = 1;
	return $s->save_clist();
}

#noop
sub _fetch_info {}

#$self -> $name
sub name {
	return $_[0]->clist()->{name};
}

#-> \%spot
#returns the first spot
sub first {
	my ($s) = @_;
	$l->trace('creat first ',$s->{id});
	return $s->create(1,$s->clist()->{url_start});
}

#$class, $id, $state -> \%self
#creates a new spot of $id at in state $state
sub create {
	my ($s,$pos,$state) = @_;
	my $class = ref($s) . '::Spot';
	my $spot = {id => $s->{id}, position => $pos, state => $state};
	$l->debug('creat new core ' , $class, ' id: ', $s->{id}, ,' position: ', $pos);
	$class->new($spot);
	return $spot;
}

package Core::Template::Spot;

my $SHA = Digest::SHA->new();

#$class, \%self -> \%self
#creates a new collection instance of $id at position $pos
sub new {
	my ($class,$self) = @_;
	$l->trace('new ',$class,' instance');
	$self->{fail} = 'not mounted';
	bless $self, $class;
	return $self;
}

#makes preparations to find objects
sub mount {
	my ($s) = @_;
	$s->{page_url} = $s->{state};
	$l->trace('mount ' . $s->{id} .' '. $s->{page_url});
	my $tree = Core::Template->_get_tree($s->{page_url});
	return undef unless $tree;
	my $ret = $s->_mount_parse($tree);
	$tree->delete();
	$l->trace(join "\n\t\t\t\t", map {"$_: " .($s->{$_}//'')} qw(src next)); #/padre display bug	
	$s->{fail} = undef if $ret;
	return $ret;
}

#not implemented
sub _mount_parse {
	$l->fatal('mount parse not implemented');
	die();
}


#-> \%element
#returns the element
sub fetch {
	my ($s) = @_;
	if ($s->{fail}) {
		$l->error('fail is set: ' . $s->{fail});
		return undef;
	}
	$l->trace('fetch object');

	my $file = DlUtil::get($s->{src},$s->{page_url});
	if ($file->is_error()) {
		$l->error('error get ' . $s->{src});
		$s->{fail} = 'could not fetch object';
		return undef;
	}
	my $blob = $file->decoded_content();

	$s->{type} = $file->header('Content-Type');
	$s->{sha1} = $SHA->add($blob)->hexdigest();

	return \$blob;
}

#-> \%element
#returns the element
sub element {
	my ($s) = @_;
	if ($s->{fail}) {
		$l->error('fail is set: ' . $s->{fail});
		return undef;
	}
	my $object = {};
	$object->{cid} = $s->{id};
	$object->{$_} = $s->{$_} for grep {defined $s->{$_}} Element::attribute_list_array();
	return Element->new($object);
}

#returns the next spot
sub next {
	my ($s) = @_;
	$l->trace('create next');
	if ($s->{fail}) {
		$l->error('fail is set: ' . $s->{fail});
		return undef;
	}
	unless ($s->{next}) {
		$l->error('no next was found');
		return undef;
	}
	my $next = {id => $s->{id}, position => $s->{position} + 1, state => $s->{next} };
	$next = ref($s)->new($next);
	return $next;
}

#accessors:
sub id { return $_[0]->{id} }
sub position { return $_[0]->{position} }


1;